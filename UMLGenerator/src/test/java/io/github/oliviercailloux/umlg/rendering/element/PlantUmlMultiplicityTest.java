package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// @UMLMultiplicity: the number of objects a property holds, on a field as on a
// method.
//
// It takes over from @UMLAssociation where the latter can do nothing: a collection of strings
// or an optional integer has no class at the other end, and is therefore no link. The
// the drawing itself is the same — brackets next to the name.
class PlantUmlMultiplicityTest {

    @UMLClass(name = "CanonicalMnemonic", strategy = UMLVisibility.PRIVATE)
    static class Mnemonic {
        @UMLMultiplicity("0..*")
        private String deprecatedAliases;

        @UMLMultiplicity("0..1")
        private int ucp;

        private String mnemonic;

        @UMLMultiplicity("0..*")
        public String names() {
            return "";
        }

        public String mnemonic() {
            return "";
        }
    }

    private static String source() {
        return PlantUmlAssert.documentFor(EntityExtractor.extract(Mnemonic.class));
    }

    @Test
    void onAField_theBracketsFollowTheType() {
        assertTrue(source().contains("- deprecatedAliases : String [0..*]"), source());
        assertTrue(source().contains("- ucp : int [0..1]"), source());
    }

    @Test
    void onAMethod_theBracketsFollowTheReturnType() {
        // The form of hand-kept diagrams: "names() : String [0..*]" reads "returns
        // zero, one or more strings".
        assertTrue(source().contains("+ names() : String [0..*]"), source());
        PlantUmlAssert.assertCompile(source());
    }

    @Test
    void withoutAnnotation_noBracketIsWritten() {
        assertTrue(source().contains("- mnemonic : String\n"), source());
        assertTrue(source().contains("+ mnemonic() : String\n"), source());
        assertFalse(source().contains("[]"), source());
    }

    @Test
    void readerSeesBrackets() throws Exception {
        String text = PlantUmlAssert.textRendered(EntityExtractor.extract(Mnemonic.class));

        assertTrue(text.contains("deprecatedAliases : String [0..*]"), text);
        assertTrue(text.contains("names() : String [0..*]"), text);
    }

    // --- What it combines with ---

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Decorated {
        @UMLDerived
        @UMLConstraint(rule = "without duplicate")
        @UMLMultiplicity("1..*")
        private String labels;
    }

    @Test
    void slashAndConstraints_frameTheMultiplicity() {
        // UML's order: the name, the type, the multiplicity, then the rules.
        String source = PlantUmlAssert.documentFor(EntityExtractor.extract(Decorated.class));

        assertTrue(source.contains("- /labels : String [1..*] {without duplicate}"), source);
        PlantUmlAssert.assertCompile(source);
    }

    // --- Ce qu'it refuse ---
    //
    // These fixtures deliberately carry no @UMLClass, and extraction is called
    // directly: the sweep of target/test-classes by the integration tests
    // would otherwise extract, and would fail on faults that are here the object of the test.

    static class MultiplicityFaulty {
        @UMLMultiplicity("3..1")
        private String labels;
    }

    @Test
    void invalidMultiplicity_isRefusedAndNamesTheMember() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(MultiplicityFaulty.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("labels"), e.getMessage());
        assertTrue(e.getMessage().contains("lower bound"), e.getMessage());
    }

    static class MethodFaulty {
        @UMLMultiplicity("many")
        public String names() {
            return "";
        }
    }

    @Test
    void invalidMultiplicityOnAMethod_isRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractMethods(MethodFaulty.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("names"), e.getMessage());
        assertTrue(e.getMessage().contains("valid UML multiplicity"), e.getMessage());
    }

    @UMLClass
    static class Target {}

    static class TwoMultiplicities {
        @UMLMultiplicity("0..*")
        @UMLAssociation(target = Target.class, targetMultiplicity = "1..*")
        private List<Target> targets;
    }

    @Test
    void withAnAssociation_isRefused() {
        // Two multiplicities on the same property: one of the two would be wrong, and nothing
        // dirait laquelle.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(TwoMultiplicities.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("targetMultiplicity"), e.getMessage());
    }
}
