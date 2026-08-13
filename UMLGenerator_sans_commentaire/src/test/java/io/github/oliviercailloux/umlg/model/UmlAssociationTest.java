package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UmlAssociationTest {


    @ParameterizedTest
    @ValueSource(strings = {"", "1", "0", "*", "0..1", "1..*", "0..*", "2..7", "3..3", "12"})
    void multipliciteBienFormee_estGardeeTelleQuelle(String multiplicite) {
        UmlAssociation association = UmlAssociation.builder("champ", "Cible")
            .sourceMultiplicity(multiplicite)
            .targetMultiplicity(multiplicite)
            .build();

        assertEquals(multiplicite, association.sourceMultiplicity());
        assertEquals(multiplicite, association.targetMultiplicity());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "n", "0..x", "1..", "..*", "*..1", "0...1", "1..2..3", "-1", "1 ", " 1", "0..*extra",
        "0..*\nclass Intruse", "5..2", "1.2",
    })
    void multipliciteMalFormeeOuIncoherente_estRefusee(String multiplicite) {
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("champ", "Cible").targetMultiplicity(multiplicite).build());
    }


    @ParameterizedTest
    @ValueSource(strings = {"rôle (principal)", "n°42", "L'acheteur", "a<b>c", "\"guillemets\""})
    void texteLibreQuelconque_estGardeTelQuel(String texte) {
        UmlAssociation association = UmlAssociation.builder("champ", "Cible")
            .sourceRole(texte).targetRole(texte).label(texte).build();

        assertEquals(texte, association.sourceRole());
        assertEquals(texte, association.targetRole());
        assertEquals(texte, association.label());
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
