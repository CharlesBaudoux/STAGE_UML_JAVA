package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// The grammar of multiplicities, exercised where it lives.
//
// It was so far through its holders — a UmlField, a UmlAssociation — and never
// directly. Yet it is what the annotation processor and the model must read in the
// the same way: what javac refuses and what the generation refuses cannot differ.
class MultiplicitiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "0", "1", "42", "0..1", "1..*", "0..*", "3..3", "999999999"})
    void validForms_areAccepted(String multiplicity) {
        assertDoesNotThrow(() -> Multiplicities.validate(multiplicity, "value"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"n", "1..n", "-1", "1.2", "1...2", "0..1..2", " ", "1 .. 2", "**",
        "1..", "..1", "1234567890"})
    void faultyForms_areRefused(String multiplicity) {
        assertThrows(IllegalArgumentException.class,
            () -> Multiplicities.validate(multiplicity, "value"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "0", "1", "42", "0..1", "1..*", "0..*", "3..3", "999999999"})
    void validForms_areRecognized(String multiplicity) {
        // The question the class promised to answer, and which it offered only in
        // raising: knowing had to go through a try, and an exception stood in for an answer.
        assertTrue(Multiplicities.isValid(multiplicity));
    }

    @ParameterizedTest
    @ValueSource(strings = {"n", "1..n", "-1", "1.2", "1...2", "0..1..2", " ", "1 .. 2", "**",
        "1..", "..1", "1234567890", "3..1"})
    void faultyForms_areRecognized(String multiplicity) {
        // The two questions cannot answer differently: they read the same rule,
        // "3..1" included, which only comparing the bounds rules out.
        assertFalse(Multiplicities.isValid(multiplicity));
    }

    @Test
    void aLowerBoundAboveTheUpperOne_isRefused() {
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> Multiplicities.validate("3..1", "targetMultiplicity"));

        assertTrue(refusal.getMessage().contains("the lower bound exceeds the upper bound"),
            refusal.getMessage());
    }

    @Test
    void theMessage_namesTheCallerParameter() {
        // That is the whole point of the second argument: the message must name what
        // the user wrote, "sourceMultiplicity" or "value", and not an internal name.
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> Multiplicities.validate("many", "sourceMultiplicity"));

        assertTrue(refusal.getMessage().startsWith("sourceMultiplicity = \"many\""),
            refusal.getMessage());
    }
}
