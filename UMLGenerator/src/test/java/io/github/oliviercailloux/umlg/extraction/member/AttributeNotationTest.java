package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// The attribute notation of an accessor: @UMLInclude(asAttribute = true).
//
// An accessor without parameter exposes a property; diagrams showing a structure
// write it "xKeycodeByXKeyName : Map<String, Short>", without parentheses. The decision is
// individual — the same reference diagram keeps "latest(): Xkeys", because a factory
// is not a property — hence an annotation, and not a project rule.
class AttributeNotationTest {

    // Without @UMLClass: see MemberDisplayTest — the integration tests scan the whole of
    // target/test-classes, and MemberExtractor n'en a not besoin.
    interface Keyboard {
        @UMLInclude(asAttribute = true, name = "xKeycodeByXKeyName")
        Map<String, Short> codeByName();

        @UMLInclude
        Keyboard withoutAliases();

        String canonical(String keyName);
    }

    @Test
    void anAccessorInAttributeNotation_appearsWithoutParentheses() {
        List<UmlField> attributes = MemberExtractor.methodsAsAttributes(Keyboard.class,
            UMLVisibility.PUBLIC, Rules.no());

        assertEquals(1, attributes.size(), attributes.toString());
        assertEquals("xKeycodeByXKeyName", attributes.get(0).name());
        assertEquals("Map<String, Short>", attributes.get(0).type());
    }

    @Test
    void theConvertedMethod_leavesTheMethodCompartment() {
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(Keyboard.class, UMLVisibility.PUBLIC);

        assertTrue(methods.stream().noneMatch(m -> m.name().equals("xKeycodeByXKeyName")),
            methods.toString());
        assertTrue(methods.stream().noneMatch(m -> m.name().equals("codeByName")),
            methods.toString());
        // The other methods, included or not, stay where they were.
        assertTrue(methods.stream().anyMatch(m -> m.name().equals("withoutAliases")),
            methods.toString());
    }

    static class Factory {
        @UMLInclude(asAttribute = true)
        public static Factory instance() {
            return new Factory();
        }
    }

    @Test
    void aStaticAccessor_keepsItsUnderline() {
        List<UmlField> attributes = MemberExtractor.methodsAsAttributes(Factory.class,
            UMLVisibility.PUBLIC, Rules.no());

        assertTrue(attributes.get(0).isStatic(), attributes.toString());
    }

    static class WithParameter {
        @UMLInclude(asAttribute = true)
        public String canonical(String keyName) {
            return keyName;
        }
    }

    @Test
    void aMethodWithParameters_isRefusedNamingTheMethod() {
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.methodsAsAttributes(WithParameter.class, UMLVisibility.PUBLIC,
                Rules.no()));

        assertTrue(refusal.getMessage().contains("takes parameters"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("canonical"), refusal.getMessage());
    }

    static class WithoutReturn {
        @UMLInclude(asAttribute = true)
        public void validate() {}
    }

    @Test
    void aMethodWithoutReturn_isRefused() {
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.methodsAsAttributes(WithoutReturn.class, UMLVisibility.PUBLIC,
                Rules.no()));

        assertTrue(refusal.getMessage().contains("returns nothing"), refusal.getMessage());
    }

    static class Hidden {
        @UMLInclude(asAttribute = true)
        @UMLExclude
        public String state() {
            return "";
        }
    }

    @Test
    void onlyUmlExcludeWinsOverTheNotation() {
        // No rule can empty this box: a rule never designates an element by its
        // name. The veto is therefore written on the member, where it reads next to what it cancels.
        Rules rules = Rules.read("""
            { "hide": ["*tat"] }
            """, "umlg.json");

        assertTrue(MemberExtractor.methodsAsAttributes(Hidden.class, UMLVisibility.PUBLIC, rules)
            .isEmpty());
    }

    @Test
    void aQualifiedPattern_isRefusedPointingToUmlExclude() {
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> Rules.read("""
                { "hide": ["Hidden.state"] }
                """, "umlg.json"));

        assertTrue(refusal.getMessage().contains("@UMLExclude"), refusal.getMessage());
    }
}
