package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Vérifie les gardes d'UmlAssociation, et surtout la frontière entre les deux régimes de
// validation : strict pour les multiplicités (vocabulaire fermé), minimal pour les textes lus
// par un humain.
class UmlAssociationTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "0", "*", "0..1", "1..*", "0..*", "2..7", "3..3", "12"})
    void multipliciteBienFormee_estAcceptee(String cardinalite) {
        UmlAssociation.builder("champ", "Cible")
            .sourceCardinality(cardinalite)
            .targetCardinality(cardinalite)
            .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "n", "0..x", "1..", "..*", "*..1", "0...1", "1..2..3", "-1", "1 ", " 1", "0..*extra",
        "0..*\nclass Intruse", "5..2", "1.2",
    })
    void multipliciteMalFormeeOuIncoherente_estRefusee(String cardinalite) {
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("champ", "Cible").targetCardinality(cardinalite).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rôle (principal)", "n°42", "L'acheteur", "a<b>c", "\"guillemets\""})
    void texteLibreQuelconque_estAccepte(String texte) {
        UmlAssociation.builder("champ", "Cible")
            .sourceRole(texte).targetRole(texte).label(texte).qualifier(texte).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void texteLibreBlanc_estRefuse(String texte) {
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("champ", "Cible").targetRole(texte).build());
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("champ", "Cible").label(texte).build());
    }

    @Test
    void valeurNulle_estRefusee() {
        assertThrows(NullPointerException.class,
            () -> UmlAssociation.builder("champ", "Cible").label(null));
        assertThrows(NullPointerException.class,
            () -> UmlAssociation.builder("champ", "Cible").type(null));
        assertThrows(NullPointerException.class, () -> UmlAssociation.builder("champ", null));
    }
}
