package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Vérifie les gardes du record UmlDependency : ce qu'il accepte, ce qu'il refuse.
class UmlDependencyTest {

    // @ParameterizedTest + @ValueSource : le test est rejoué une fois par chaîne de la liste,
    // celle-ci arrivant dans le paramètre de la méthode. Chaque valeur compte comme un test.
    @ParameterizedTest
    @ValueSource(strings = {"facture via", "n'utilise qu'en lecture", "appelle <b>souvent</b>",
        "a\"b", "Réf. n°42"})
    void labelQuelconque_estAccepte(String label) {
        // Un motif est un texte destiné à être lu : interdire les caractères qui gênent le
        // moteur de rendu placerait le défaut au mauvais endroit. Sa sûreté est obtenue par
        // encodage à la sortie.
        assertEquals(label, new UmlDependency("Cible", label).label());
    }

    // La contrepartie : un label fait uniquement d'espaces n'est pas un texte, c'est un oubli.
    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\n"})
    void labelBlanc_estRefuse(String label) {
        assertThrows(IllegalArgumentException.class, () -> new UmlDependency("Cible", label));
    }

    @Test
    void cibleVideOuNulle_estRefusee() {
        // Deux types d'exception distincts, et c'est voulu : null est une faute de programmation
        // (NullPointerException, via checkNotNull), une chaîne blanche une valeur invalide
        // (IllegalArgumentException, via checkArgument).
        assertThrows(NullPointerException.class, () -> new UmlDependency(null, ""));
        assertThrows(NullPointerException.class, () -> new UmlDependency("Cible", null));
        assertThrows(IllegalArgumentException.class, () -> new UmlDependency("  ", ""));
    }

    // La fabrique raccourcie doit produire un label vide, et non null.
    @Test
    void dependanceSansMotif_aUnLabelVide() {
        assertEquals("", UmlDependency.toTarget("Cible").label());
    }
}
