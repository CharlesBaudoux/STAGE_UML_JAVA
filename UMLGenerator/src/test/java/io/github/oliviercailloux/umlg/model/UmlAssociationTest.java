package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UmlAssociationTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "0", "*", "0..1", "1..*", "0..*", "2..7", "3..3", "12"})
    void wellFormedMultiplicity_isKeptAsIs(String multiplicity) {
        UmlAssociation association = UmlAssociation.builder("field", "Target")
            .sourceMultiplicity(multiplicity)
            .targetMultiplicity(multiplicity)
            .build();

        assertEquals(multiplicity, association.sourceMultiplicity());
        assertEquals(multiplicity, association.targetMultiplicity());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "n", "0..x", "1..", "..*", "*..1", "0...1", "1..2..3", "-1", "1 ", " 1", "0..*extra",
        "0..*\nclass Intruse", "5..2", "1.2",
    })
    void malformedOrInconsistentMultiplicity_isRefused(String multiplicity) {
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("field", "Target").targetMultiplicity(multiplicity).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"role (main)", "no.42", "The buyer", "a<b>c", "\"quotes\""})
    void anyFreeText_isKeptAsIs(String text) {
        UmlAssociation association = UmlAssociation.builder("field", "Target")
            .sourceRole(text).targetRole(text).label(text).build();

        assertEquals(text, association.sourceRole());
        assertEquals(text, association.targetRole());
        assertEquals(text, association.label());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void blankFreeText_isRefused(String text) {
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("field", "Target").targetRole(text).build());
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("field", "Target").label(text).build());
    }

    @Test
    void nullValue_isRefused() {
        assertThrows(NullPointerException.class,
            () -> UmlAssociation.builder("field", "Target").label(null));
        assertThrows(NullPointerException.class,
            () -> UmlAssociation.builder("field", "Target").type(null));
        assertThrows(NullPointerException.class, () -> UmlAssociation.builder("field", null));
    }
}
