package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Checks the guards of the UmlDependency record: what it accepts, what it refuses.
class UmlDependencyTest {

    // @ParameterizedTest + @ValueSource: the test is replayed once per string of the list,
    // the latter arriving in the method parameter. Each value counts as one test.
    @ParameterizedTest
    @ValueSource(strings = {"invoice via", "reads only", "calls <b>often</b>",
        "a\"b", "Ref. no.42"})
    void anyLabel_isAccepted(String label) {
        // A pattern is a text meant to be read: forbidding the characters that hamper the
        // the rendering engine would put the defect in the wrong place. Its safety is obtained by
        // encoding on output.
        assertEquals(label, new UmlDependency("Target", label).label());
    }

    // The counterpart: a label made of spaces only is not a text, it is an oversight.
    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\n"})
    void blankLabel_isRefused(String label) {
        assertThrows(IllegalArgumentException.class, () -> new UmlDependency("Target", label));
    }

    @Test
    void emptyOrNullTarget_isRefused() {
        // Two distinct exception types, and that is intended: null is a programming fault
        // (NullPointerException, through checkNotNull), a blank string an invalid value
        // (IllegalArgumentException, via checkArgument).
        assertThrows(NullPointerException.class, () -> new UmlDependency(null, ""));
        assertThrows(NullPointerException.class, () -> new UmlDependency("Target", null));
        assertThrows(IllegalArgumentException.class, () -> new UmlDependency("  ", ""));
    }

    // The short factory must produce an empty label, and not null.
    @Test
    void dependencyWithoutReason_hasAnEmptyLabel() {
        assertEquals("", UmlDependency.toTarget("Target").label());
    }
}
