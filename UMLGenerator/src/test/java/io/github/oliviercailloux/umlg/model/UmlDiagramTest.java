package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UmlDiagramTest {

    @ParameterizedTest
    @ValueSource(strings = {"Architecture of the Billing module", "Overview", "Ref. no.42",
        "a\"b"})
    void anyTitle_isAccepted(String title) {
        assertEquals(title, new UmlDiagram(title, UMLFlow.TOP_TO_BOTTOM, true).title());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "   "})
    void blankTitle_isRefused(String title) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new UmlDiagram(title, UMLFlow.TOP_TO_BOTTOM, true));

        assertTrue(e.getMessage().contains("whitespace"), e.getMessage());
    }

    @Test
    void emptyTitle_isAccepted() {
        assertFalse(new UmlDiagram("", UMLFlow.TOP_TO_BOTTOM, true).hasTitle());
    }

    @Test
    void declaredTitle_isReported() {
        assertTrue(new UmlDiagram("Billing", UMLFlow.TOP_TO_BOTTOM, true).hasTitle());
    }

    @Test
    void nullValues_areRefused() {
        assertThrows(NullPointerException.class,
            () -> new UmlDiagram(null, UMLFlow.TOP_TO_BOTTOM, true));
        assertThrows(NullPointerException.class, () -> new UmlDiagram("", null, true));
    }

    @Test
    void defaultSettings_hideEmptyMembers() {
        assertTrue(UmlDiagram.defaults().hideEmptyMembers());
    }

    @Test
    void defaultSettings_haveNeitherTitleNorImposedDirection() {
        assertFalse(UmlDiagram.defaults().hasTitle());
        assertEquals(UMLFlow.TOP_TO_BOTTOM, UmlDiagram.defaults().direction());
    }
}
