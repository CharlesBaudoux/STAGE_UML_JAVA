package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UMLGeneratorTest {

    private static final String MARQUEUR_DU_THEME_UMLG = "skinparam ClassBorderColor #33526E";

    @UMLClass(name = "CobayeDeLaFacade", strategy = UMLVisibility.PUBLIC)
    static class CobayeDeLaFacade {
        public String nom;
    }

    private static Path unRepertoireDUneClasse(Path tmp) throws IOException {
        String chemin = CobayeDeLaFacade.class.getName().replace('.', '/') + ".class";
        Path racine = tmp.resolve("classes");
        Path destination = racine.resolve(chemin);
        Files.createDirectories(destination.getParent());
        Files.copy(Path.of("target", "test-classes").resolve(chemin), destination);
        return racine;
    }

    private static UMLGenerator.Builder surLeCobaye(Path tmp) throws IOException {
        return UMLGenerator.builder().sourceDirectory(unRepertoireDUneClasse(tmp).toString());
    }


    @Test
    void sansDossierSource_leBuilderRefuseDeConstruire() {
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().outputFile("test.png").build(),
            "Le dossier source n'a pas de défaut raisonnable.");
    }

    @Test
    void unDossierSourceInexistant_estRefuseDesLAppel() {
        assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory("/un/chemin/totalement/invente"));
    }

    @Test
    void uneExtensionInconnue_estRefuseeDesLAppel(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile("d.jpg"));
    }

    @Test
    void unStyleNull_estRefuse(@TempDir Path tmp) {
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).style(null));
    }


    @Test
    void unFichierEtUnDossierDeSortie_sontRefusesEnsemble(@TempDir Path tmp) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> UMLGenerator.builder()
                .sourceDirectory(tmp.toString())
                .outputFile("archi.png")
                .outputDirectory("docs")
                .build());

        assertTrue(e.getMessage().contains("archi.png") && e.getMessage().contains("docs"),
            "Le message doit nommer les deux sorties demandées : " + e.getMessage());
    }

    @Test
    void unFormatDemandeSansDossier_estRefuse(@TempDir Path tmp) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> UMLGenerator.builder()
                .sourceDirectory(tmp.toString())
                .outputFile("a.png")
                .outputFormat("svg")
                .build());

        assertTrue(e.getMessage().contains("svg"), e.getMessage());
    }

    @Test
    void conserverLaGenerationPrecedenteSansDossier_estRefuse(@TempDir Path tmp) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> UMLGenerator.builder()
                .sourceDirectory(tmp.toString())
                .outputFile("archi.svg")
                .keepPrevious(true)
                .build());

        assertTrue(e.getMessage().contains("keepPrevious"), e.getMessage());
    }

    @Test
    void nePasConserverLaGenerationPrecedente_resteLiciteSansDossier(@TempDir Path tmp) {
        assertDoesNotThrow(() -> UMLGenerator.builder()
            .sourceDirectory(tmp.toString())
            .outputFile("archi.svg")
            .keepPrevious(false)
            .build());
    }

    @Test
    void unFormatDemandeAvecUnDossier_estApplique(@TempDir Path tmp) throws IOException {
        Path dossier = tmp.resolve("diagrammes");

        RapportDeGeneration rapport = surLeCobaye(tmp)
            .outputDirectory(dossier.toString())
            .outputFormat("png")
            .build()
            .generate();

        assertEquals(List.of(dossier.resolve("projet.png")), rapport.fichiers());
    }

    @Test
    void leFichierParDefaut_neCompteJamaisPourUneDemande(@TempDir Path tmp) throws IOException {
        Path dossier = tmp.resolve("diagrammes");

        RapportDeGeneration rapport = surLeCobaye(tmp)
            .outputDirectory(dossier.toString())
            .build()
            .generate();

        assertEquals(List.of(dossier.resolve("projet.svg")), rapport.fichiers());
    }

    @Test
    void desVuesEnModeFichierUnique_sontSignalees(@TempDir Path tmp) throws IOException {
        Path source = unRepertoireDUneClasse(tmp);
        Files.writeString(source.resolve("umlg.json"), "{ \"viewsByPackage\": true }",
            StandardCharsets.UTF_8);

        RapportDeGeneration rapport = UMLGenerator.builder()
            .sourceDirectory(source.toString())
            .outputFile(tmp.resolve("archi.svg").toString())
            .build()
            .generate();

        assertTrue(rapport.avertissements().stream().anyMatch(a -> a.contains("partial view")),
            "Les vues perdues doivent être dites : " + rapport.avertissements());
    }

    @Test
    void desVuesEnModeDossier_neSontPasSignalees(@TempDir Path tmp) throws IOException {
        Path source = unRepertoireDUneClasse(tmp);
        Files.writeString(source.resolve("umlg.json"), "{ \"viewsByPackage\": true }",
            StandardCharsets.UTF_8);

        RapportDeGeneration rapport = UMLGenerator.builder()
            .sourceDirectory(source.toString())
            .outputDirectory(tmp.resolve("diagrammes").toString())
            .build()
            .generate();

        assertEquals(List.of(), rapport.avertissements(),
            "Rien n'est perdu, donc rien n'est à dire.");
        assertEquals(2, rapport.fichiers().size(), "L'intégrale et la vue de paquetage.");
    }


    @Test
    void generate_ecritLeFichierDemandeEtSaSource(@TempDir Path tmp) throws IOException {
        Path sortie = tmp.resolve("resultat.svg");

        RapportDeGeneration rapport =
            surLeCobaye(tmp).outputFile(sortie.toString()).build().generate();

        assertTrue(Files.exists(sortie), "L'image demandée doit être écrite.");
        assertEquals(1, rapport.classesLues());
        assertTrue(Files.readString(tmp.resolve("resultat.puml"), StandardCharsets.UTF_8)
            .contains("CobayeDeLaFacade"), "La boîte du cobaye doit être dans la source.");
    }

    @Test
    void styleUmlg_poseLeThemeDeLaBibliothequeDansLaSource(@TempDir Path tmp) throws IOException {
        surLeCobaye(tmp)
            .outputFile(tmp.resolve("theme.svg").toString())
            .style(UmlStyle.UMLG)
            .build()
            .generate();

        assertTrue(Files.readString(tmp.resolve("theme.puml"), StandardCharsets.UTF_8)
            .contains(MARQUEUR_DU_THEME_UMLG));
    }

    @Test
    void styleParDefaut_laisseLApparenceDePlantUml(@TempDir Path tmp) throws IOException {
        surLeCobaye(tmp).outputFile(tmp.resolve("nu.svg").toString()).build().generate();

        assertFalse(Files.readString(tmp.resolve("nu.puml"), StandardCharsets.UTF_8)
            .contains(MARQUEUR_DU_THEME_UMLG), "Ne rien demander, c'est ne rien imposer.");
    }


    @Test
    void keepPrevious_ecritLaGenerationSuivanteACote(@TempDir Path tmp) throws IOException {
        Path dossier = tmp.resolve("diagrammes");
        UMLGenerator generateur = surLeCobaye(tmp)
            .outputDirectory(dossier.toString())
            .keepPrevious(true)
            .build();

        generateur.generate();
        RapportDeGeneration second = generateur.generate();

        assertTrue(Files.exists(dossier.resolve("projet.svg")),
            "La première génération doit rester intacte.");
        assertEquals(List.of(tmp.resolve("diagrammes-2").resolve("projet.svg")),
            second.fichiers(), "La seconde doit aller dans le dossier voisin numéroté.");
    }

    @Test
    void sansKeepPrevious_laGenerationSuivanteRemplaceLaPrecedente(@TempDir Path tmp)
        throws IOException {
        Path dossier = tmp.resolve("diagrammes");
        UMLGenerator generateur =
            surLeCobaye(tmp).outputDirectory(dossier.toString()).build();

        generateur.generate();
        RapportDeGeneration second = generateur.generate();

        assertEquals(List.of(dossier.resolve("projet.svg")), second.fichiers());
        assertFalse(Files.exists(tmp.resolve("diagrammes-2")),
            "Le dossier décrit l'état courant du code, et rien ne s'accumule à côté.");
    }


    @Test
    void sansClasspath_uneClasseDontLeParentVitDansUnJar_estIllisible(@TempDir Path tmp)
        throws Exception {
        Path classes = unProjetDontLeParentEstDansUnJar(tmp);
        List<String> dits = new ArrayList<>();

        RuntimeException e;
        try (Avertissements.Canal canal = Avertissements.redirectTo(dits::add)) {
            e = assertThrows(RuntimeException.class, () -> UMLGenerator.builder()
                .sourceDirectory(classes.toString())
                .outputFile(tmp.resolve("archi.svg").toString())
                .build()
                .generate());
        }

        assertTrue(dits.stream().anyMatch(a -> a.contains("appli.Fille")),
            "L'échec de chargement doit être dit, et nommer la classe : " + dits);
        assertTrue(e.getMessage().contains("nothing to draw"), e.getMessage());
    }

    @Test
    void avecLeClasspath_uneClasseDontLeParentVitDansUnJar_estDessinee(@TempDir Path tmp)
        throws Exception {
        Path classes = unProjetDontLeParentEstDansUnJar(tmp);

        RapportDeGeneration rapport = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .classpath(List.of(tmp.resolve("lib").resolve("tiers.jar").toString()))
            .outputFile(tmp.resolve("archi.svg").toString())
            .build()
            .generate();

        assertEquals(1, rapport.classesLues());
        assertTrue(Files.readString(tmp.resolve("archi.puml"), StandardCharsets.UTF_8)
            .contains("Fille"), "La boîte de la classe héritière doit être dessinée.");
    }

    @Test
    void unClasspathNull_estRefuse(@TempDir Path tmp) {
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).classpath(null));
    }

    private static Path unProjetDontLeParentEstDansUnJar(Path tmp) throws Exception {
        Path classes = tmp.resolve("compilees");
        CompilationSansProcesseur.compiler(tmp.resolve("src"), classes,
            "tiers/Base.java", """
                package tiers;

                public class Base {}
                """,
            "appli/Fille.java", """
                package appli;

                import io.github.oliviercailloux.umlg.annotation.UMLClass;
                import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

                @UMLClass(strategy = UMLVisibility.PUBLIC)
                public class Fille extends tiers.Base {
                    public String nom;
                }
                """);

        Path jar = tmp.resolve("lib").resolve("tiers.jar");
        Files.createDirectories(jar.getParent());
        String entree = "tiers/Base.class";
        try (JarOutputStream sortie = new JarOutputStream(Files.newOutputStream(jar))) {
            sortie.putNextEntry(new JarEntry(entree));
            sortie.write(Files.readAllBytes(classes.resolve(entree)));
            sortie.closeEntry();
        }
        Files.delete(classes.resolve(entree));
        Files.delete(classes.resolve("tiers"));
        return classes;
    }


    @Test
    void ligneDeCommande_avecUnNomSansExtension_ecritUnDossierDeVues(@TempDir Path tmp)
        throws Exception {
        Path sortie = tmp.resolve("diagrammes");

        UMLGenerator.main(
            new String[] {unRepertoireDUneClasse(tmp).toString(), sortie.toString()});

        assertTrue(Files.isDirectory(sortie), "Un nom sans extension demande un dossier.");
        assertTrue(Files.exists(sortie.resolve("projet.svg")), "La vue intégrale y est écrite.");
    }

    @Test
    void ligneDeCommande_avecUneExtension_ecritCeSeulFichier(@TempDir Path tmp) throws Exception {
        Path fichier = tmp.resolve("architecture.svg");

        UMLGenerator.main(
            new String[] {unRepertoireDUneClasse(tmp).toString(), fichier.toString()});

        assertTrue(Files.isRegularFile(fichier));
    }

    @Test
    void ligneDeCommande_accepteLeFormatPng(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("diagrammes");

        UMLGenerator.main(
            new String[] {unRepertoireDUneClasse(tmp).toString(), sortie.toString(), "png"});

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
