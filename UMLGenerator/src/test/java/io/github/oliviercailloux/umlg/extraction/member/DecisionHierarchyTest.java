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

// The hierarchy of decisions, exercised where two mechanisms cross.
//
// The order is the same throughout the library: @UMLExclude first, @UMLInclude next,
// then the two general defaults — the class strategy and the "hide" patterns of the rules
// rules, which are on the same footing because they are of the same nature: overall settings, which
// nobody made about this member.
//
// Each of the four compartments reimplemented its own sequence of guards, and the four
// differed. The tests did not see it: each exercised a single mechanism, never a pattern
// AND a record, a pattern AND an association. This file holds nothing but crossings.
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

    // --- A pattern against a record component ---

    // The fixtures carry no @UMLClass, except the association targets, which must carry it
    // to be aimed at: MemberExtractor does not need it, and the integration tests scan
    // all target/test-classes.
    record Bag(String logText, String kept) {}

    @Test
    void aPattern_doesNotUndoARecordComponent() {
        // A component is the very definition of the record: a box silencing it would no longer say
        // what the record IS. A pattern is a general default, like the strategy, and it does not
        // undoes a definition no more than it does.
        List<UmlField> fields =
            MemberExtractor.extractFields(Bag.class, UMLVisibility.NONE, rules("log*"));

        assertEquals(List.of("kept", "logText"), names(fields), fields.toString());
    }

    record BagExcluded(@UMLExclude String logText, String kept) {}

    @Test
    void onARecordComponent_onlyUmlExcludeHasTheLastWord() {
        // The other half of the rule: what a general default cannot do, the individual
        // the individual one can — otherwise nothing could ever remove a component.
        List<UmlField> fields =
            MemberExtractor.extractFields(BagExcluded.class, UMLVisibility.NONE, rules("log*"));

        assertEquals(List.of("kept"), names(fields), fields.toString());
    }

    // --- A pattern against an association written in the box ---

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
        // The documentation presents ARROW and ATTRIBUTE as two writings of the same link. Under a
        // pattern, the stroke stayed and the line vanished: changing notation erased the
        // link, which nothing announced.
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

    // --- The same crossing, on the other three compartments ---

    static class Verbose {
        @UMLInclude
        public String logKept;

        public String logTu;

        @UMLInclude(name = "account")
        public String logRenamed;
    }

    @Test
    void aPattern_undoesNeitherAnInclude_norItsRenaming() {
        // The individual decision beats the general setting, and the name it gives holds too:
        // the two halves of the same question, asked in the same place.
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
        // asAttribute is written in an @UMLInclude: the request is individual, and the pattern does not
        // undoes it here no more than elsewhere.
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
        // A literal escaped everything holding for the other members: no pattern
        // never reached it, and nothing documented it.
        ImmutableList<String> literals = MemberExtractor.extractLiterals(Currency.class,
            UMLVisibility.PUBLIC, rules("LOG*"));

        assertEquals(List.of("Euro", "LOGGED"), literals, literals.toString());
    }

    // --- What the generation now says about asAttribute on a field ---

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
        // The processor stops compilation on this code; the generation ignored it without a word.
        // The same code therefore got two answers depending on whether it was compiled with the processor or
        // while it was drawn without it. The text is that of the processor's error, word for word.
        ImmutableList<String> said = warnings(
            () -> MemberExtractor.extractFields(NotationOnAField.class, UMLVisibility.PUBLIC));

        assertEquals(1, said.size(), said.toString());
        assertTrue(said.get(0).contains("@UMLInclude(asAttribute = true) is for methods; a field "
            + "is already an attribute."), said.get(0));
        assertTrue(said.get(0).contains("NotationOnAField.state"), said.get(0));
    }

    @Test
    void anOrdinaryField_saysNothing() {
        // Restraint counts as much as reporting: a warning that would fire on
        // of ordinary code would teach one to stop reading them.
        assertTrue(warnings(
            () -> MemberExtractor.extractFields(Verbose.class, UMLVisibility.PUBLIC)).isEmpty());
    }
}
