package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UMLGeneratorTest {

    @Test
    void testConfigurationValide(@TempDir Path dossierTemporaire) {
        // Act : On configure le générateur avec des paramètres parfaits
        UMLGenerator generator = UMLGenerator.builder()
                .sourceDirectory(dossierTemporaire.toString())
                .outputFile("mon_architecture.svg")
                .build();

        // Assert : Si on arrive ici, c'est que toutes les validations ont réussi
        assertNotNull(generator, "Le générateur aurait dû être instancié avec succès.");
    }

    @Test
    void testEchecSansDossierSource() {
        // Act & Assert : On tente de build sans fournir le dossier source
        assertThrows(NullPointerException.class, () -> {
            UMLGenerator.builder()
                    .outputFile("test.png")
                    .build(); // L'erreur doit exploser ici
        }, "Le builder doit interdire la construction si aucun dossier source n'est fourni.");
    }

    @Test
    void testEchecAvecDossierInvalide() {
        // Act & Assert : On fournit un chemin fantôme
        assertThrows(IllegalArgumentException.class, () -> {
            UMLGenerator.builder()
                    .sourceDirectory("/un/chemin/totalement/invente/qui/n/existe/pas");
        }, "Le builder doit rejeter un dossier source qui n'existe pas physiquement.");
    }

    @Test
    void testEchecAvecExtensionFichierInvalide(@TempDir Path dossierTemporaire) {
        // Act & Assert : On tente de demander du PDF ou du JPG (non supportés)
        assertThrows(IllegalArgumentException.class, () -> {
            UMLGenerator.builder()
                    .sourceDirectory(dossierTemporaire.toString())
                    .outputFile("diagramme.jpg"); // L'extension invalide
        }, "Le builder doit rejeter formellement toute extension autre que .png ou .svg.");
    }

    @Test
    void testExecutionCompleteSansErreur(@TempDir Path dossierSortie) {
        Path cheminFichierSortie = dossierSortie.resolve("resultat.png");

        // On pointe sur le dossier test-classes qui contient nos cobayes
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
