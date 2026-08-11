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

    // --- Couleur : vocabulaire fermé, donc liste blanche ---

    @Test
    void couleurInvalide_estRejetee() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateColor("pas une couleur !", getClass()),
            "Une valeur qui n'est ni un code hexadécimal ni un nom de couleur doit être rejetée.");
    }

    @Test
    void couleurNommee_estAcceptee() {
        // Le vocabulaire des couleurs est fermé, mais il ne se limite pas à l'hexadécimal :
        // « LightBlue » est une couleur PlantUML parfaitement valide, que l'ancienne
        // expression régulière refusait.
        assertDoesNotThrow(() -> ClassHeaderValidator.validateColor("LightBlue", getClass()));
    }

    // --- Nom et stéréotype : textes libres, donc contrainte minimale ---

    @Test
    void nomAvecCaracteresSpeciaux_estAccepte() {
        // Ces noms sont légitimes et rendus fidèlement : les refuser serait faire porter à
        // l'utilisateur une contrainte qui ne regarde que le moteur de rendu.
        assertDoesNotThrow(() -> {
            ClassHeaderValidator.validateName("Nom\"Injecte", getClass());
            ClassHeaderValidator.validateName("Compte (bancaire)", getClass());
            ClassHeaderValidator.validateName("L'Utilisateur", getClass());
            ClassHeaderValidator.validateName("Client<T>", getClass());
        });
    }

    @Test
    void nomBlanc_estRejete() {
        // La seule règle qui subsiste, et elle est métier : un nom doit désigner quelque chose.
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateName("   ", getClass()),
            "Un nom composé uniquement d'espaces doit être rejeté.");
    }

    // Les chevrons ne sont pas interdits ici : ils seront encodés au rendu par
    // PlantUmlEscaper.stereotype, et s'afficheront tels quels.
    @Test
    void stereotypeAvecChevrons_estAccepte() {
        assertDoesNotThrow(() -> ClassHeaderValidator.validateStereotype("<<Hack>>", getClass()));
    }

    @Test
    void stereotypeBlanc_estRejete() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateStereotype("   ", getClass()));
    }

    // --- Ce que l'échappement vide, la validation doit le refuser ---
    //
    // isBlank() ne tient pas les caractères de contrôle pour des blancs, alors que l'échappement
    // les supprime : un nom fait de ces seuls caractères traversait la validation, puis engendrait
    // « class "" as C » — que PlantUML refuse. La garde du writer levait alors une
    // IllegalStateException accusant « un défaut de la bibliothèque », message que l'utilisateur
    // ne pouvait pas suivre. La question est donc posée au texte échappé, celui qui sera écrit.

    @Test
    void nomFaitDeCaracteresInvisibles_estRejete() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateName("\1\2", getClass()),
            "Ce nom se réduit à la chaîne vide dans le fichier engendré.");
    }

    @Test
    void stereotypeFaitDeCaracteresInvisibles_estRejete() {
        // Sa manifestation propre : « class \"E\" as E <<>> », que le moteur refuse aussi.
        assertThrows(IllegalArgumentException.class,
            () -> ClassHeaderValidator.validateStereotype("\1\2", getClass()));
    }

    @Test
    void nomOrdinaireAvecUnCaractereDeControle_resteAccepte() {
        // La correction ne durcit pas la règle : il suffit qu'il reste quelque chose à dessiner.
        assertDoesNotThrow(() -> ClassHeaderValidator.validateName("Compte\1", getClass()));
        assertDoesNotThrow(() -> ClassHeaderValidator.validateStereotype("entity\1", getClass()));
    }
}
