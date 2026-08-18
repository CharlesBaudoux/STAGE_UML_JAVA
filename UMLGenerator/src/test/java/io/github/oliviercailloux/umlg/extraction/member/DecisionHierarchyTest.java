package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.Warnings;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.relation.AssociationExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DecisionHierarchyTest {

    private static Rules rules(String... patterns) {
        String list = Arrays.stream(patterns)
            .map(pattern -> "\"" + pattern + "\"")
            .collect(Collectors.joining(", "));
        return Rules.read("{ \"hide\": [" + list + "] }", "umlg.json");
    }

    private static List<String> names(List<UmlField> fields) {
        return fields.stream().map(UmlField::name).toList();
    }

    record Bag(String logText, String kept) {}

    @Test
    void aPattern_doesNotUndoARecordComponent() {
        List<UmlField> fields =
            MemberExtractor.extractFields(Bag.class, UMLVisibility.NONE, rules("log*"));

        assertEquals(List.of("kept", "logText"), names(fields), fields.toString());
    }

    record BagExcluded(@UMLExclude String logText, String kept) {}

    @Test
    void onARecordComponent_onlyUmlExcludeHasTheLastWord() {
        List<UmlField> fields =
            MemberExtractor.extractFields(BagExcluded.class, UMLVisibility.NONE, rules("log*"));

        assertEquals(List.of("kept"), names(fields), fields.toString());
    }

    @UMLClass
    static class Target {}

    static class Log {
        @UMLAssociation(targetMultiplicity = "0..*", notation = UMLNotation.ATTRIBUTE)
        private List<Target> logTargets;
    }

    static class LogAsStroke {
        @UMLAssociation(targetMultiplicity = "0..*")
        private List<Target> logTargets;
    }

    @Test
    void aPattern_doesNotMakeAnInBoxLinkVanish() {
        List<UmlField> fields =
            MemberExtractor.extractFields(Log.class, UMLVisibility.NONE, rules("log*"));

        assertEquals(List.of("logTargets"), names(fields), fields.toString());
        assertEquals("Target", fields.get(0).type());
        assertEquals("0..*", fields.get(0).multiplicity());
    }

    @Test
    void bothNotations_survive_together_theSamePattern() {
        ImmutableList<UmlAssociation> strokes =
            new AssociationExtractor().extract(LogAsStroke.class);
        List<UmlField> lines = MemberExtractor.extractFields(Log.class, UMLVisibility.NONE,
            rules("log*"));

        assertEquals(1, strokes.size(), strokes.toString());
        assertEquals(1, lines.size(), lines.toString());
    }

    static class LogExcluded {
        @UMLExclude
        @UMLAssociation(targetMultiplicity = "0..*", notation = UMLNotation.ATTRIBUTE)
        private List<Target> logTargets;
    }

    @Test
    void onAnAssociationWrittenInTheBox_onlyUmlExcludeHasTheLastWord() {
        assertTrue(MemberExtractor
            .extractFields(LogExcluded.class, UMLVisibility.NONE, rules("log*")).isEmpty());
    }

    static class Verbose {
        @UMLInclude
        public String logKept;

        public String logTu;

        @UMLInclude(name = "account")
        public String logRenamed;
    }

    @Test
    void aPattern_undoesNeitherAnInclude_norItsRenaming() {
        List<UmlField> fields =
            MemberExtractor.extractFields(Verbose.class, UMLVisibility.PUBLIC, rules("log*"));

        assertEquals(List.of("logKept", "account"), names(fields), fields.toString());
    }

    static class Accessors {
        @UMLInclude(asAttribute = true)
        public String logState() {
            return "";
        }
    }

    @Test
    void aPattern_doesNotUndoTheAttributeNotation() {
        List<UmlField> fields = MemberExtractor.methodsAsAttributes(Accessors.class,
            UMLVisibility.NONE, rules("log*"));

        assertEquals(List.of("logState"), names(fields), fields.toString());
    }

    enum Currency {
        Euro,

        @UMLInclude
        LOGGED,

        LOGISTICS
    }

    @Test
    void aPattern_reachesALiteral_butNotAnIncludedLiteral() {
        ImmutableList<String> literals = MemberExtractor.extractLiterals(Currency.class,
            UMLVisibility.PUBLIC, rules("LOG*"));

        assertEquals(List.of("Euro", "LOGGED"), literals, literals.toString());
    }

    static class NotationOnAField {
        @UMLInclude(asAttribute = true)
        public String state;
    }

    private static ImmutableList<String> warnings(Runnable action) {
        ImmutableList.Builder<String> said = ImmutableList.builder();
        try (Warnings.Channel channel = Warnings.redirectTo(said::add)) {
            action.run();
        }
        return said.build();
    }

    @Test
    void asAttributeOnAField_isReported_asTheProcessorRefusesIt() {
        ImmutableList<String> said = warnings(
            () -> MemberExtractor.extractFields(NotationOnAField.class, UMLVisibility.PUBLIC));

        assertEquals(1, said.size(), said.toString());
        assertTrue(said.get(0).contains("@UMLInclude(asAttribute = true) is for methods; a field "
            + "is already an attribute."), said.get(0));
        assertTrue(said.get(0).contains("NotationOnAField.state"), said.get(0));
    }

    @Test
    void anOrdinaryField_saysNothing() {
        assertTrue(warnings(
            () -> MemberExtractor.extractFields(Verbose.class, UMLVisibility.PUBLIC)).isEmpty());
    }
}
