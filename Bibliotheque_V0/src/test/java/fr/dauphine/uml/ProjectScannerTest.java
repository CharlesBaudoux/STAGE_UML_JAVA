package fr.dauphine.uml;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScannerTest {

    // 1. Nos cobayes pour le test
    // Une classe interne annotée : elle DOIT être détectée
    @UMLClass
    static class ClasseCobayeAnnotee {}

    // Une classe interne NON annotée : elle DOIT être ignorée
    static class ClasseCobayeIgnoree {}

    @Test
    void testScanDossierValide() throws Exception {
        // Arrange : On pointe vers le dossier de sortie des tests de Maven
        Path cheminTestClasses = Paths.get("target", "test-classes");

        // Act : On lance le scanner sur notre propre dossier de compilation de test
        UmlProject projet = ProjectScanner.scan(cheminTestClasses);

        // Assert : On vérifie le comportement du scanner sur nos deux cobayes
        boolean cobayeAnnoteTrouve = projet.entities().stream()
                .anyMatch(e -> e.name().contains("ClasseCobayeAnnotee"));
                
        boolean cobayeIgnoreTrouve = projet.entities().stream()
                .anyMatch(e -> e.name().contains("ClasseCobayeIgnoree"));

        assertTrue(cobayeAnnoteTrouve, "Le scanner aurait dû détecter la classe annotée @UMLClass.");
        assertFalse(cobayeIgnoreTrouve, "Le scanner n'aurait pas dû embarquer la classe sans annotation.");
    }

    @Test
    void testScanRejetteDossierInexistant() {
        // Arrange
        Path dossierFantome = Paths.get("target", "un-dossier-qui-n-existe-pas");

        // Act & Assert : Vérification de la sécurité Guava checkArgument
        assertThrows(IllegalArgumentException.class, () -> {
            ProjectScanner.scan(dossierFantome);
        }, "Le scanner doit lever une IllegalArgumentException si le dossier n'existe pas.");
    }

    @Test
    void testScanRejetteFichierSimple(@TempDir Path dossierTemporaire) throws Exception {
        // Arrange : On crée un fichier standard (pas un dossier)
        Path fichierSimple = dossierTemporaire.resolve("texte.txt");
        Files.writeString(fichierSimple, "Contenu d'un fichier classique");

        // Act & Assert : Vérification que le scanner refuse les fichiers simples
        assertThrows(IllegalArgumentException.class, () -> {
            ProjectScanner.scan(fichierSimple);
        }, "Le scanner doit refuser un chemin qui pointe vers un fichier au lieu d'un répertoire.");
    }

    @Test
    void testScanRejetteNull() {
        // Act & Assert : Vérification de la sécurité Guava checkNotNull
        assertThrows(NullPointerException.class, () -> {
            ProjectScanner.scan(null);
        }, "Le scanner doit lever une NullPointerException si le chemin fourni est null.");
    }
}