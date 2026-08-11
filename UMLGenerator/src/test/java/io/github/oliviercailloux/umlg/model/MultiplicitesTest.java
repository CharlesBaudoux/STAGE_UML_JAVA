package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// La grammaire des multiplicités, éprouvée là où elle vit.
//
// Elle l'était jusqu'ici à travers ses porteurs — un UmlField, un UmlAssociation — et jamais
// directement. C'est pourtant elle que le processeur d'annotations et le modèle doivent lire de
// la même façon : ce que javac refuse et ce que la génération refuse ne peuvent pas différer.
class MultiplicitesTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "0", "1", "42", "0..1", "1..*", "0..*", "3..3", "999999999"})
    void lesFormesLicites_sontAcceptees(String multiplicite) {
        assertDoesNotThrow(() -> Multiplicites.valider(multiplicite, "value"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"n", "1..n", "-1", "1.2", "1...2", "0..1..2", " ", "1 .. 2", "**",
        "1..", "..1", "1234567890"})
    void lesFormesFautives_sontRefusees(String multiplicite) {
        assertThrows(IllegalArgumentException.class,
            () -> Multiplicites.valider(multiplicite, "value"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "0", "1", "42", "0..1", "1..*", "0..*", "3..3", "999999999"})
    void lesFormesLicites_sontReconnues(String multiplicite) {
        // La question que la classe promettait de savoir répondre, et qu'elle n'offrait qu'en
        // levant : savoir devait passer par un try, et une exception tenait lieu de réponse.
        assertTrue(Multiplicites.estValide(multiplicite));
    }

    @ParameterizedTest
    @ValueSource(strings = {"n", "1..n", "-1", "1.2", "1...2", "0..1..2", " ", "1 .. 2", "**",
        "1..", "..1", "1234567890", "3..1"})
    void lesFormesFautives_sontReconnues(String multiplicite) {
        // Les deux questions ne peuvent pas répondre différemment : elles lisent la même règle,
        // « 3..1 » compris, que seule la comparaison des bornes écarte.
        assertFalse(Multiplicites.estValide(multiplicite));
    }

    @Test
    void uneBorneBasseSuperieureALaHaute_estRefusee() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> Multiplicites.valider("3..1", "targetCardinality"));

        assertTrue(refus.getMessage().contains("the lower bound exceeds the upper bound"),
            refus.getMessage());
    }

    @Test
    void leMessage_nommeLeParametreDeLAppelant() {
        // C'est tout l'intérêt du second argument : le message doit désigner ce que
        // l'utilisateur a écrit, « sourceCardinality » ou « value », et non un nom interne.
        var refus = assertThrows(IllegalArgumentException.class,
            () -> Multiplicites.valider("beaucoup", "sourceCardinality"));

        assertTrue(refus.getMessage().startsWith("sourceCardinality = \"beaucoup\""),
            refus.getMessage());
    }
}
