package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Vérifie les gardes du record UmlDependency : ce qu'il accepte, ce qu'il refuse.
class UmlDependencyTest {

    @ParameterizedTest
    @ValueSource(strings = {"facture via", "n'utilise qu'en lecture", "appelle <b>souvent</b>",
        "a\"b", "Réf. n°42"})
    void labelQuelconque_estAccepte(String label) {
        assertEquals(label, new UmlDependency("Cible", label).label());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\n"})
    void labelBlanc_estRefuse(String label) {
        assertThrows(IllegalArgumentException.class, () -> new UmlDependency("Cible", label));
    }

    @Test
    void cibleVideOuNulle_estRefusee() {
        assertThrows(NullPointerException.class, () -> new UmlDependency(null, ""));
        assertThrows(NullPointerException.class, () -> new UmlDependency("Cible", null));
        assertThrows(IllegalArgumentException.class, () -> new UmlDependency("  ", ""));
    }

    @Test
    void dependanceSansMotif_aUnLabelVide() {
        assertEquals("", UmlDependency.toTarget("Cible").label());
    }
}
