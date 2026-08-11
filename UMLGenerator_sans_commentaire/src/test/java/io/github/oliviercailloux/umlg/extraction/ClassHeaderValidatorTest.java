package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ClassHeaderValidatorTest {

    @Test
    void couleurInvalide_estRejetee() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateColor("pas une couleur !", getClass()),
            "Une valeur qui n'est ni un code hexadécimal ni un nom de couleur doit être rejetée.");
    }

    @Test
    void couleurNommee_estAcceptee() {
        assertDoesNotThrow(() -> ClassHeaderValidator.validateColor("LightBlue", getClass()));
    }

    @Test
    void nomAvecCaracteresSpeciaux_estAccepte() {
        assertDoesNotThrow(() -> {
            ClassHeaderValidator.validateName("Nom\"Injecte", getClass());
            ClassHeaderValidator.validateName("Compte (bancaire)", getClass());
            ClassHeaderValidator.validateName("L'Utilisateur", getClass());
            ClassHeaderValidator.validateName("Client<T>", getClass());
        });
    }

    @Test
    void nomBlanc_estRejete() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateName("   ", getClass()),
            "Un nom composé uniquement d'espaces doit être rejeté.");
    }

    @Test
    void stereotypeAvecChevrons_estAccepte() {
        assertDoesNotThrow(() -> ClassHeaderValidator.validateStereotype("<<Hack>>", getClass()));
    }

    @Test
    void stereotypeBlanc_estRejete() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateStereotype("   ", getClass()));
    }
}
