package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemberDisplayTest {

    private static Rules rules(String json) {
        return Rules.read(json, "umlg.json");
    }

    static class Abbreviated {
        @UMLInclude(name = "xKeycodeByXKeyName")
        public int codeByName;

        @UMLInclude(name = "byKeysymMnemonic")
        public int byMnemonic() {
            return 0;
        }
    }

    @Test
    void aRenamedField_appearsUnderTheDiagramName() {
        List<UmlField> fields =
            MemberExtractor.extractFields(Abbreviated.class, UMLVisibility.PUBLIC);

        assertEquals("xKeycodeByXKeyName", fields.get(0).name());
    }

    @Test
    void aRenamedMethod_appearsUnderTheDiagramName() {
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(Abbreviated.class, UMLVisibility.PUBLIC);

        assertEquals("byKeysymMnemonic", methods.get(0).name());
    }

    static class EmptyRenaming {
        @UMLInclude(name = "   ")
        public int field;
    }

    @Test
    void aNameMadeOfSpaces_isRefusedNamingTheField() {
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(EmptyRenaming.class, UMLVisibility.PUBLIC));

        assertTrue(refusal.getMessage().contains("whitespace"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("field"), refusal.getMessage());
    }

    @UMLClass
    static class Target {}

    static class TwoNames {
        @UMLInclude(name = "oneName")
        @UMLAssociation(target = Target.class, targetRole = "another",
            notation = UMLNotation.ATTRIBUTE)
        public Target field;
    }

    @Test
    void targetRoleAndName_onTheSameLine_areRefused() {
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(TwoNames.class, UMLVisibility.PUBLIC));

        assertTrue(refusal.getMessage().contains("two names"), refusal.getMessage());
    }

    static class SourceImpossible {
        @UMLAssociation(target = Target.class, sourceMultiplicity = "3..1",
            notation = UMLNotation.ATTRIBUTE)
        public Target field;
    }

    static class BlankSourceRole {
        @UMLAssociation(target = Target.class, sourceRole = "   ",
            notation = UMLNotation.ATTRIBUTE)
        public Target field;
    }

    static class BlankLabel {
        @UMLAssociation(target = Target.class, label = "   ", notation = UMLNotation.ATTRIBUTE)
        public Target field;
    }

    @Test
    void aFaultyStrokeParameter_isRefusedEvenInAttributeNotation() {
        var multiplicity = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(SourceImpossible.class, UMLVisibility.PUBLIC));
        assertTrue(multiplicity.getMessage().contains("sourceMultiplicity"),
            multiplicity.getMessage());

        var role = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(BlankSourceRole.class, UMLVisibility.PUBLIC));
        assertTrue(role.getMessage().contains("sourceRole"), role.getMessage());

        var label = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(BlankLabel.class, UMLVisibility.PUBLIC));
        assertTrue(label.getMessage().contains("label"), label.getMessage());
    }

    static class Unordered {
        public int zebra;
        public int ostrich;
        public int blackbird;

        public void zoo() {}

        public void arch() {}
    }

    @Test
    void byDefault_theAlphabetSorts() {
        List<UmlField> fields =
            MemberExtractor.extractFields(Unordered.class, UMLVisibility.PUBLIC);

        assertEquals(List.of("blackbird", "ostrich", "zebra"),
            fields.stream().map(UmlField::name).toList());
    }

    @Test
    void onRequest_theCodeOrderIsKept() {
        Rules rules = rules("""
            { "order": "declaration" }
            """);

        List<UmlField> fields =
            MemberExtractor.extractFields(Unordered.class, UMLVisibility.PUBLIC, rules);
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(Unordered.class, UMLVisibility.PUBLIC, rules);

        assertEquals(List.of("zebra", "ostrich", "blackbird"),
            fields.stream().map(UmlField::name).toList());
        assertEquals(List.of("zoo", "arch"),
            methods.stream().map(UmlMethod::name).toList());
    }

    static class Overloaded {
        public void perform(String text) {}

        public void perform(int count) {}

        public void before() {}
    }

    @Test
    void overloads_alsoKeepTheirOrder() {
        List<UmlMethod> methods = MemberExtractor.extractMethods(Overloaded.class,
            UMLVisibility.PUBLIC, rules("""
                { "order": "declaration" }
                """));

        assertEquals("perform", methods.get(0).name());
        assertEquals("String", methods.get(0).parameters().get(0).type());
        assertEquals("perform", methods.get(1).name());
        assertEquals("int", methods.get(1).parameters().get(0).type());
        assertEquals("before", methods.get(2).name());
    }

    static class Verbose {
        public void configure(String filePath, int columnsCount) {}
    }

    @Test
    void parameterNames_fallSilentOnRequest() {
        List<UmlMethod> methods = MemberExtractor.extractMethods(Verbose.class,
            UMLVisibility.PUBLIC, rules("""
                { "omit": ["parameter names"] }
                """));

        assertTrue(methods.get(0).parameters().stream().noneMatch(p -> p.isNamed()),
            methods.toString());
        assertEquals("String", methods.get(0).parameters().get(0).type());
    }

    static class Parent {
        @UMLInclude
        public String string;

        @UMLInclude
        public String svg;

        @UMLInclude
        public String remains;
    }

    @Test
    void aPattern_doesNotWinOverAnInclude() {
        List<UmlField> fields = MemberExtractor.extractFields(Parent.class, UMLVisibility.NONE,
            rules("""
                { "hide": ["s*"] }
                """));

        assertEquals(List.of("remains", "string", "svg"),
            fields.stream().map(UmlField::name).toList());
    }

    @Test
    void aQualifiedPattern_isRefusedPointingToUmlExclude() {
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> rules("""
                { "hide": ["Parent.string"] }
                """));

        assertTrue(refusal.getMessage().contains("@UMLExclude"), refusal.getMessage());
    }
}
