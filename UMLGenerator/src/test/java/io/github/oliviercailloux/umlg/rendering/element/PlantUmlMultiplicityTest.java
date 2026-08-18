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

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Decorated {
        @UMLDerived
        @UMLConstraint(rule = "without duplicate")
        @UMLMultiplicity("1..*")
        private String labels;
    }

    @Test
    void slashAndConstraints_frameTheMultiplicity() {
        String source = PlantUmlAssert.documentFor(EntityExtractor.extract(Decorated.class));

        assertTrue(source.contains("- /labels : String [1..*] {without duplicate}"), source);
        PlantUmlAssert.assertCompile(source);
    }

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
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(TwoMultiplicities.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("targetMultiplicity"), e.getMessage());
    }
}
