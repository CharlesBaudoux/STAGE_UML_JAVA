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
        // Act : On configure le générateur avec des paramètres parfaits
        UMLGenerator generator = UMLGenerator.builder()
                .sourceDirectory(dossierTemporaire.toString())
                .outputFile("mon_architecture.svg")
                .build();

        // Assert : Si on arrive ici, c'est que toutes les validations ont réussi
        assertNotNull(generator, "Le générateur aurait dû être instancié avec succès.");
    }

    @Test
    void styleRefuseNull(@TempDir Path dossierTemporaire) {
        // Le style a un défaut, mais « null » n'en est pas un : ce serait un oubli, et le
        // laisser passer ferait échouer le rendu bien plus loin, sans dire pourquoi.
        assertThrows(NullPointerException.class, () -> UMLGenerator.builder()
            .sourceDirectory(dossierTemporaire.toString())
            .style(null));
    }

    @Test
    void styleFacultatif_leDefautSuffitAConstruire(@TempDir Path dossierTemporaire) {
        // L'apparence est le seul paramètre à défaut raisonnable des trois : PlantUML sait
        // dessiner sans qu'on lui dicte ses couleurs.
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

    // Le seul test qui parcourt toute la chaîne : scan réel de target/test-classes (où se
    // trouvent les cobayes annotés des autres tests), puis écriture d'une vraie image.
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

    // --- La ligne de commande ---

    @Test
    void ligneDeCommande_avecUnNomSansExtension_ecritUnDossierDeVues(@TempDir Path tmp)
        throws Exception {
        // Le trou que ce test protège : outputDirectory existait dans le builder, et la ligne de
        // commande n'y donnait pas accès — la fonctionnalité principale était inatteignable pour
        // qui n'écrivait pas sa propre classe de lancement.
        Path sortie = tmp.resolve("diagrammes");

        UMLGenerator.main(new String[] {"target/test-classes", sortie.toString()});

        assertTrue(Files.isDirectory(sortie), "Un nom sans extension demande un dossier.");
        assertTrue(Files.exists(sortie.resolve("projet.svg")), "La vue intégrale y est écrite.");
    }

    @Test
    void ligneDeCommande_avecUneExtension_ecritCeSeulFichier(@TempDir Path tmp) throws Exception {
        // Personne n'appelle « architecture.svg » un répertoire : l'extension suffit à dire ce
        // qu'on veut, et évite une option de plus.
        Path fichier = tmp.resolve("architecture.svg");

        UMLGenerator.main(new String[] {"target/test-classes", fichier.toString()});

        assertTrue(Files.isRegularFile(fichier));
    }

    @Test
    void ligneDeCommande_accepteLeFormatPng(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("diagrammes");

        UMLGenerator.main(new String[] {"target/test-classes", sortie.toString(), "png"});

        assertTrue(Files.exists(sortie.resolve("projet.png")));
    }
}
