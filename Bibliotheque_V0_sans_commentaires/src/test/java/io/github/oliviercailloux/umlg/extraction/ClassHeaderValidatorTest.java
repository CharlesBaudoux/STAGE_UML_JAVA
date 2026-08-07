package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

// Éprouve la frontière entre les deux régimes de validation de l'en-tête d'une classe : liste
// blanche pour la couleur, contrainte minimale pour les textes lus par un humain.
//
// Le test est dans le même paquetage que ClassHeaderValidator : c'est ce qui lui donne accès à
// cette classe, qui n'est pas publique.
//
// getClass() sert de classe fautive dans les messages : peu importe laquelle, seul compte le
// fait qu'il y en ait une.
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
