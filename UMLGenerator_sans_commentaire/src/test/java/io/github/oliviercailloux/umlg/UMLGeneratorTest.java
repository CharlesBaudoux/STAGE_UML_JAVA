package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void ligneDeCommande_avecUnNomSansExtension_ecritUnDossierDeVues(@TempDir Path tmp)
        throws Exception {
        Path sortie = tmp.resolve("diagrammes");

        UMLGenerator.main(new String[] {"target/test-classes", sortie.toString()});

        assertTrue(Files.isDirectory(sortie), "Un nom sans extension demande un dossier.");
        assertTrue(Files.exists(sortie.resolve("projet.svg")), "La vue intégrale y est écrite.");
    }

    @Test
    void ligneDeCommande_avecUneExtension_ecritCeSeulFichier(@TempDir Path tmp) throws Exception {
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

    @Test
    void unNomDeFichierNu_designeLeRepertoireCourant() {
        Path dossier = UMLGenerator.dossierDeSortie(Path.of("diagramme.svg"));

        assertEquals(Path.of(""), dossier);
        assertEquals(Path.of(UMLGenerator.FICHIER_DES_CLASSES_IGNOREES),
            dossier.resolve(UMLGenerator.FICHIER_DES_CLASSES_IGNOREES));
    }

    @Test
    void unNomNuEtLeMemeEnPointSlash_menentAuMemeFichier() {
        Path nu = UMLGenerator.dossierDeSortie(Path.of("diagramme.svg"))
            .resolve(UMLGenerator.FICHIER_DES_CLASSES_IGNOREES);
        Path pointSlash = UMLGenerator.dossierDeSortie(Path.of("./diagramme.svg"))
            .resolve(UMLGenerator.FICHIER_DES_CLASSES_IGNOREES);

        assertEquals(nu.toAbsolutePath().normalize(), pointSlash.toAbsolutePath().normalize());
    }

    @Test
    void unCheminAvecDossier_gardeSonDossier() {
        assertEquals(Path.of("docs"), UMLGenerator.dossierDeSortie(Path.of("docs", "archi.svg")));
    }
}
