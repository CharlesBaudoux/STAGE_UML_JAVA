package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Checks the guards of UmlAssociation, and above all the border between the two regimes of
// validation: strict for multiplicities (a closed vocabulary), minimal for texts read
// by a human.
class UmlAssociationTest {

    // --- Multiplicities: a closed vocabulary ---

    // Accepted, and kept as is: the necessary counterpart of the refusal test below.
    // A validation normalizing along the way — "3..3" brought back to "3", the space removed —
    // would let the drawing say something other than what the author wrote, and nothing would see it.
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

    // The list covers the usual faults: lone letter, truncated interval, reversed bounds,
    // stray space, decimal — and "0..*\nclass Intruder", which would try to inject a line
    // in the PlantUML file.
    @ParameterizedTest
    @ValueSource(strings = {
        "n", "0..x", "1..", "..*", "*..1", "0...1", "1..2..3", "-1", "1 ", " 1", "0..*extra",
        "0..*\nclass Intruse", "5..2", "1.2",
    })
    void malformedOrInconsistentMultiplicity_isRefused(String multiplicity) {
        // "5..2" is the interesting case: syntactically flawless, empty of meaning. A
        // a purely lexical check would let it through, and the diagram would display
        // without flinching an impossible multiplicity.
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("field", "Target").targetMultiplicity(multiplicity).build());
    }

    // --- Free texts: a minimal constraint ---

    @ParameterizedTest
    @ValueSource(strings = {"role (main)", "no.42", "The buyer", "a<b>c", "\"quotes\""})
    void anyFreeText_isKeptAsIs(String text) {
        // A role or a label is meant to be read by a human: forbidding the characters
        // hampering the rendering engine would put the defect in the wrong place. Their safety is
        // obtained by encoding on output — hence as late as possible, and the model keeps the text
        // of origin. That is what these three assertions hold: an escaping surfacing here
        // would make the text encoded twice on the drawing.
        UmlAssociation association = UmlAssociation.builder("field", "Target")
            .sourceRole(text).targetRole(text).label(text).build();

        assertEquals(text, association.sourceRole());
        assertEquals(text, association.targetRole());
        assertEquals(text, association.label());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void blankFreeText_isRefused(String text) {
        // Disguised emptiness is the only fault: a displayed text must designate something.
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("field", "Target").targetRole(text).build());
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("field", "Target").label(text).build());
    }

    @Test
    void nullValue_isRefused() {
        // The first two fail as early as the setter (checkNotNull is called there), the third as early as
        // the creation of the builder: none waits for build(). A null is reported as early as possible.
        assertThrows(NullPointerException.class,
            () -> UmlAssociation.builder("field", "Target").label(null));
        assertThrows(NullPointerException.class,
            () -> UmlAssociation.builder("field", "Target").type(null));
        assertThrows(NullPointerException.class, () -> UmlAssociation.builder("field", null));
    }
}
