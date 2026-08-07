package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// La façade vue de l'extérieur : ce que le builder accepte, ce qu'il refuse, et une génération
// complète de bout en bout.
//
// Chaque refus est vérifié au bon appel : le dossier invalide échoue dès sourceDirectory(),
// l'extension invalide dès outputFile(), et l'absence de dossier source seulement à build().
// Ce n'est pas un détail : c'est ce qui donne un message pointant sur la ligne fautive.
class UMLGeneratorTest {

    @Test
    void testConfigurationValide(@TempDir Path dossierTemporaire) {
        UMLGenerator generator = UMLGenerator.builder()
                .sourceDirectory(dossierTemporaire.toString())
                .outputFile("mon_architecture.svg")
                .build();

        assertNotNull(generator, "Le générateur aurait dû être instancié avec succès.");
    }

    @Test
    void styleRefuseNull(@TempDir Path dossierTemporaire) {
        assertThrows(NullPointerException.class, () -> UMLGenerator.builder()
            .sourceDirectory(dossierTemporaire.toString())
            .style(null));
    }

    @Test
    void styleFacultatif_leDefautSuffitAConstruire(@TempDir Path dossierTemporaire) {
        assertNotNull(UMLGenerator.builder()
            .sourceDirectory(dossierTemporaire.toString())
            .outputFile("diagramme.svg")
            .build());
    }

    @Test
    void styleExplicite_estAccepte(@TempDir Path dossierTemporaire) {
        assertNotNull(UMLGenerator.builder()
            .sourceDirectory(dossierTemporaire.toString())
            .outputFile("diagramme.svg")
            .style(UmlStyle.UMLG)
            .build());
    }

    @Test
    void testEchecSansDossierSource() {
        assertThrows(NullPointerException.class, () -> {
            UMLGenerator.builder()
                    .outputFile("test.png")
                    .build();
        }, "Le builder doit interdire la construction si aucun dossier source n'est fourni.");
    }

    @Test
    void testEchecAvecDossierInvalide() {
        assertThrows(IllegalArgumentException.class, () -> {
            UMLGenerator.builder()
                    .sourceDirectory("/un/chemin/totalement/invente/qui/n/existe/pas");
        }, "Le builder doit rejeter un dossier source qui n'existe pas physiquement.");
    }

    @Test
    void testEchecAvecExtensionFichierInvalide(@TempDir Path dossierTemporaire) {
        assertThrows(IllegalArgumentException.class, () -> {
            UMLGenerator.builder()
                    .sourceDirectory(dossierTemporaire.toString())
                    .outputFile("diagramme.jpg");
        }, "Le builder doit rejeter formellement toute extension autre que .png ou .svg.");
    }

    @Test
    void testExecutionCompleteSansErreur(@TempDir Path dossierSortie) {
        Path cheminFichierSortie = dossierSortie.resolve("resultat.png");

        UMLGenerator generator = UMLGenerator.builder()
                .sourceDirectory("target/test-classes")
                .outputFile(cheminFichierSortie.toString())
                .build();

        assertDoesNotThrow(() -> {
            generator.generate();
        }, "La génération complète ne doit lever aucune exception sur un environnement valide.");

        assertTrue(Files.exists(cheminFichierSortie), "Le fichier final aurait dû être produit.");
    }
}
