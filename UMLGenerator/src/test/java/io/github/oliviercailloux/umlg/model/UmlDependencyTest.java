package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UmlDependencyTest {

    @ParameterizedTest
    @ValueSource(strings = {"invoice via", "reads only", "calls <b>often</b>",
        "a\"b", "Ref. no.42"})
    void anyLabel_isAccepted(String label) {
        assertEquals(label, new UmlDependency("Target", label).label());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\n"})
    void blankLabel_isRefused(String label) {
        assertThrows(IllegalArgumentException.class, () -> new UmlDependency("Target", label));
    }

    @Test
    void emptyOrNullTarget_isRefused() {
        assertThrows(NullPointerException.class, () -> new UmlDependency(null, ""));
        assertThrows(NullPointerException.class, () -> new UmlDependency("Target", null));
        assertThrows(IllegalArgumentException.class, () -> new UmlDependency("  ", ""));
    }

    @Test
    void dependencyWithoutReason_hasAnEmptyLabel() {
        assertEquals("", UmlDependency.toTarget("Target").label());
    }
}
