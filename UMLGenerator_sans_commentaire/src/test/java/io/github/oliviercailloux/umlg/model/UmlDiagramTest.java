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
    @ValueSource(strings = {"Architecture du module Facturation", "Vue d'ensemble", "Réf. n°42",
        "a\"b"})
    void titreQuelconque_estAccepte(String titre) {
        assertEquals(titre, new UmlDiagram(titre, UMLFlow.TOP_TO_BOTTOM, true).title());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "   "})
    void titreBlanc_estRefuse(String titre) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new UmlDiagram(titre, UMLFlow.TOP_TO_BOTTOM, true));

        assertTrue(e.getMessage().contains("whitespace"), e.getMessage());
    }

    @Test
    void titreVide_estAccepte() {
        assertFalse(new UmlDiagram("", UMLFlow.TOP_TO_BOTTOM, true).aUnTitre());
    }

    @Test
    void titreDeclare_estSignale() {
        assertTrue(new UmlDiagram("Facturation", UMLFlow.TOP_TO_BOTTOM, true).aUnTitre());
    }

    @Test
    void valeursNulles_sontRefusees() {
        assertThrows(NullPointerException.class,
            () -> new UmlDiagram(null, UMLFlow.TOP_TO_BOTTOM, true));
        assertThrows(NullPointerException.class, () -> new UmlDiagram("", null, true));
    }

    @Test
    void reglagesParDefaut_masquentLesMembresVides() {
        assertTrue(UmlDiagram.defaut().hideEmptyMembers());
    }

    @Test
    void reglagesParDefaut_nOntNiTitreNiSensImpose() {
        assertFalse(UmlDiagram.defaut().aUnTitre());
        assertEquals(UMLFlow.TOP_TO_BOTTOM, UmlDiagram.defaut().direction());
    }
}
