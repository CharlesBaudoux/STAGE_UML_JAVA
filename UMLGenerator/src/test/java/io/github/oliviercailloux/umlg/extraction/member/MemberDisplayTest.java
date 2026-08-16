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

// What the boxes show, under which name, in which order, and in which form.
//
// Four recent freedoms, and one same purpose: letting the generated diagram
// match the reference diagram of a project — the README's, kept by hand — without
// rewriting the code. Renaming a member (@UMLInclude(name = ...)), keeping the writing order
// ("\"order\": \"declaration\""), abbreviate the signatures ("\"omit\": [\"parameter names\"]"), and silence
// un member
// general, never a member named explicitly — that is @UMLExclude's job.
class MemberDisplayTest {

    private static Rules rules(String json) {
        return Rules.read(json, "umlg.json");
    }

    // --- Renaming: @UMLInclude(name = ...) ---

    // The fixtures carry no @UMLClass: MemberExtractor does not need it, and the tests
    // integration tests scan the whole of target/test-classes — a deliberately faulty fixture
    // were annotated it would fail their generation. Only Target is: an association requires
    // that the class it aims at be in the diagram.
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
        // Two names for the same line: nothing would say which wins, and the library does
        // does not choose in place of code that contradicts itself.
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(TwoNames.class, UMLVisibility.PUBLIC));

        assertTrue(refusal.getMessage().contains("two names"), refusal.getMessage());
    }

    // --- Stroke parameters, under a notation that draws no stroke ---

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
        // The annotation processor already refused those three values whatever the
        // notation; the generation, for its part, did not read them at all, so that the same code
        // stopped at compilation and got through at drawing time. Both halves now ask the
        // the same question — an impossible multiplicity stays impossible, whether it ends up drawn
        // or not, and the first switch to ARROW would make it appear.
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

    // --- Declaration order: "order declaration" ---

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
        // Reflection ignores that order; it is read back in the .class file, where the tables of
        // members follow the source.
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
        // Two overloads share a name: only the descriptor tells them apart, and the order of the
        // the source must survive that ambiguity.
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

    // --- Les signatures courtes : « "omit": ["parameter names"] » ---

    static class Verbose {
        public void configure(String filePath, int columnsCount) {}
    }

    @Test
    void parameterNames_fallSilentOnRequest() {
        List<UmlMethod> methods = MemberExtractor.extractMethods(Verbose.class,
            UMLVisibility.PUBLIC, rules("""
                { "omit": ["parameter names"] }
                """));

        // The type stays — it is what carries the information — but the name vanishes, as in
        // a hand-kept diagram: "configure(String, int)".
        assertTrue(methods.get(0).parameters().stream().noneMatch(p -> p.isNamed()),
            methods.toString());
        assertEquals("String", methods.get(0).parameters().get(0).type());
    }

    // --- The annotation always wins over the rule, without exception ---

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
        // The rule as always, and now with no exception to remember: the individual decision
        // beats the general setting. A pattern is a family of names, and @UMLInclude brings back everything
        // that it drops.
        List<UmlField> fields = MemberExtractor.extractFields(Parent.class, UMLVisibility.NONE,
            rules("""
                { "hide": ["s*"] }
                """));

        assertEquals(List.of("remains", "string", "svg"),
            fields.stream().map(UmlField::name).toList());
    }

    @Test
    void aQualifiedPattern_isRefusedPointingToUmlExclude() {
        // It was the only rule that beat @UMLInclude, and the single exception to the sentence
        // "the annotation always wins". It went away with the exception.
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> rules("""
                { "hide": ["Parent.string"] }
                """));

        assertTrue(refusal.getMessage().contains("@UMLExclude"), refusal.getMessage());
    }
}
