package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Checks the guards of the UmlDiagram record, and what a project declaring nothing is worth.
class UmlDiagramTest {

    @ParameterizedTest
    @ValueSource(strings = {"Architecture of the Billing module", "Overview", "Ref. no.42",
        "a\"b"})
    void anyTitle_isAccepted(String title) {
        // A title is a text meant to be read: its safety is obtained by encoding on
        // output, and not by forbidding characters here.
        assertEquals(title, new UmlDiagram(title, UMLFlow.TOP_TO_BOTTOM, true).title());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "   "})
    void blankTitle_isRefused(String title) {
        // The empty string says "no title"; a string of spaces would reserve the height of a
        // a title nobody would read.
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
        // The default decides the nominal case, that of a project setting nothing: the default
        // the default of @UMLClass showing no member, not hiding would draw
        // empty rectangles under each name.
        assertTrue(UmlDiagram.defaults().hideEmptyMembers());
    }

    @Test
    void defaultSettings_haveNeitherTitleNorImposedDirection() {
        assertFalse(UmlDiagram.defaults().hasTitle());
        assertEquals(UMLFlow.TOP_TO_BOTTOM, UmlDiagram.defaults().direction());
    }
}
