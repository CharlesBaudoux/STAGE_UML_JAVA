package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertTrue(Multiplicites.estValide(multiplicite));
    }

    @ParameterizedTest
    @ValueSource(strings = {"n", "1..n", "-1", "1.2", "1...2", "0..1..2", " ", "1 .. 2", "**",
        "1..", "..1", "1234567890", "3..1"})
    void lesFormesFautives_sontReconnues(String multiplicite) {
        assertFalse(Multiplicites.estValide(multiplicite));
    }

    @Test
    void uneBorneBasseSuperieureALaHaute_estRefusee() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> Multiplicites.valider("3..1", "targetMultiplicity"));

        assertTrue(refus.getMessage().contains("the lower bound exceeds the upper bound"),
            refus.getMessage());
    }

    @Test
    void leMessage_nommeLeParametreDeLAppelant() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> Multiplicites.valider("beaucoup", "sourceMultiplicity"));

        assertTrue(refus.getMessage().startsWith("sourceMultiplicity = \"beaucoup\""),
            refus.getMessage());
    }
}
