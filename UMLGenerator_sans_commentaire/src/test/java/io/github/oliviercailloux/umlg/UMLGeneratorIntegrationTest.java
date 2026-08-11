package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.ProjectScanner;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import io.github.oliviercailloux.umlg.rendering.PlantUmlWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UMLGeneratorIntegrationTest {

    private static final byte[] ENTETE_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private static void compiler(Path racineSources, Path sortie, String cheminRelatif, String code)
        throws IOException {
        Path fichier = racineSources.resolve(cheminRelatif);
        Files.createDirectories(fichier.getParent());
        Files.writeString(fichier, code, StandardCharsets.UTF_8);
        Files.createDirectories(sortie);

        JavaCompiler compilateur = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager gestionnaire =
            compilateur.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {

            boolean ok = compilateur.getTask(null, gestionnaire, null,
                List.of("-proc:none", "-classpath", cheminDeCompilation(), "-d",
                    sortie.toString()),
                null, gestionnaire.getJavaFileObjects(fichier.toFile())).call();
            if (!ok) {
                throw new IllegalStateException("Le cobaye n'a pas compilé :\n" + code);
            }
        }
    }

    private static String cheminDeCompilation() {
        return Stream.of(System.getProperty("java.class.path"),
                System.getProperty("jdk.module.path"))
            .filter(chemin -> chemin != null && !chemin.isBlank())
            .collect(Collectors.joining(File.pathSeparator));
    }

    @Test
    void projetAnnote_produitUnSvgContenantLArchitecture(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/banque/Compte.java", """
            package com.banque;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLExclude;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(stereotype = "Entity", color = "#DDEEFF", strategy = UMLVisibility.PRIVATE)
            public class Compte {
                private double solde;

                @UMLExclude
                private String traceInterne;

                public void deposer(double montant) {}
                public double getSolde() { return solde; }
            }
            """);
        Files.writeString(classes.resolve("umlg.rules"), "hide get*\n",
            StandardCharsets.UTF_8);

        Path sortie = tmp.resolve("architecture.svg");

        UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(sortie.toString())
            .build()
            .generate();

        String svg = Files.readString(sortie, StandardCharsets.UTF_8);

        assertFalse(svg.toLowerCase().contains("syntax error"),
            "Le diagramme livré est une image d'erreur :\n" + svg);
        assertTrue(svg.contains("Compte"), "La classe annotée doit apparaître.");
        assertTrue(svg.contains("solde"), "L'attribut privé doit apparaître (strategy PRIVATE).");
        assertTrue(svg.contains("deposer"), "La méthode métier doit apparaître.");
        assertTrue(svg.contains("Entity"), "Le stéréotype doit apparaître.");
        assertFalse(svg.contains("traceInterne"),
            "@UMLExclude doit masquer l'attribut jusqu'au SVG.");
        assertFalse(svg.contains("getSolde"),
            "\"hide get*\" doit tenir depuis le fichier de règles jusqu'au SVG.");
    }

    @Test
    void projetSansAucuneAnnotation_echoueAvecUnMessageUtile(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/banque/Neutre.java", """
            package com.banque;

            public class Neutre {}
            """);
        Path sortie = tmp.resolve("architecture.svg");

        UMLGenerator generateur = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(sortie.toString())
            .build();

        RuntimeException e = assertThrows(RuntimeException.class, generateur::generate);

        assertTrue(e.getMessage().contains(classes.toString()),
            "Le dossier source doit figurer dans le message : " + e.getMessage());
        assertTrue(e.getMessage().contains(sortie.toString()),
            "Le fichier de sortie doit figurer dans le message : " + e.getMessage());
        assertTrue(e.getCause() != null, "La cause d'origine doit être conservée.");
        assertFalse(Files.exists(sortie), "Aucun fichier ne doit être écrit en cas d'échec.");
    }

    @Test
    void generate_rendLeCompteRendu_etNommeToutesLesIgnoreesDansUnFichier(@TempDir Path tmp)
        throws Exception {
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/banque/Compte.java", """
            package com.banque;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Compte {}

            class Oubliee {}

            class Ecartee {}
            """);
        Path dossier = tmp.resolve("diagrammes");

        RapportDeGeneration rapport = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputDirectory(dossier.toString())
            .build()
            .generate();

        assertEquals(1, rapport.classesLues());
        assertEquals(List.of("Ecartee", "Oubliee"), rapport.classesIgnorees());

        Path liste = dossier.resolve(UMLGenerator.FICHIER_DES_CLASSES_IGNOREES);
        assertTrue(Files.exists(liste), "La liste complète doit être écrite dans le dossier.");
        String contenu = Files.readString(liste, StandardCharsets.UTF_8);
        assertTrue(contenu.contains("Oubliee") && contenu.contains("Ecartee"), contenu);

        String puml = Files.readString(dossier.resolve("projet.puml"), StandardCharsets.UTF_8);
        assertTrue(puml.startsWith("@startuml"), puml);
        assertTrue(puml.contains("Compte"), puml);
    }

    @Test
    void enModeFichierUnique_laListeEstEcriteAussi(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/banque/Compte.java", """
            package com.banque;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Compte {}

            class Oubliee {}
            """);

        RapportDeGeneration rapport = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(tmp.resolve("archi.svg").toString())
            .build()
            .generate();

        Path liste = tmp.resolve(UMLGenerator.FICHIER_DES_CLASSES_IGNOREES);
        assertTrue(Files.exists(liste),
            "La liste doit accompagner l'image, en mode fichier unique comme en mode dossier.");
        assertTrue(Files.readString(liste, StandardCharsets.UTF_8).contains("Oubliee"));

        assertTrue(rapport.resume().contains("archi.svg"), rapport.resume());
    }

    @Test
    void sansClasseIgnoree_leFichierPerimeEstRetire(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/banque/Compte.java", """
            package com.banque;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Compte {}
            """);
        Path dossier = tmp.resolve("diagrammes");
        Files.createDirectories(dossier);
        Path liste = dossier.resolve(UMLGenerator.FICHIER_DES_CLASSES_IGNOREES);
        Files.writeString(liste, "Perimee\n", StandardCharsets.UTF_8);

        UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputDirectory(dossier.toString())
            .build()
            .generate();

        assertFalse(Files.exists(liste), "La liste périmée doit disparaître.");
    }

    @Test
    void lesCouleursConnues_sontExposeesParLaFacade() {
        var couleurs = UMLGenerator.couleursConnues();

        assertTrue(couleurs.contains("LightBlue"), "Le catalogue de PlantUML doit être exposé.");
        assertTrue(couleurs.contains("red"));
    }

    @Test
    void echecDEcriture_estEnrobeAvecLeContexte(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/banque/Compte.java", """
            package com.banque;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Compte {}
            """);

        UMLGenerator generateur = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(tmp.resolve("dossier/absent/architecture.svg").toString())
            .build();

        RuntimeException e = assertThrows(RuntimeException.class, generateur::generate);
        assertTrue(e.getMessage().contains("generate the UML diagram"), e.getMessage());
    }

    @Test
    void generationRepetee_estIdempotente(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/banque/Compte.java", """
            package com.banque;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Compte {
                public double solde;
                public void deposer(double montant) {}
            }
            """);
        Path sortie = tmp.resolve("architecture.svg");

        UMLGenerator generateur = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(sortie.toString())
            .build();

        generateur.generate();
        String premier = Files.readString(sortie, StandardCharsets.UTF_8);
        generateur.generate();
        String second = Files.readString(sortie, StandardCharsets.UTF_8);

        assertTrue(second.contains("Compte"));
        assertTrue(premier.equals(second),
            "Deux générations identiques doivent donner le même SVG.");
    }

    @Test
    void toutesLesClassesCobayesDuProjet_produisentUnDiagrammeValide() throws Exception {
        UmlProject projet = ProjectScanner.scan(Paths.get("target", "test-classes")).projet();

        String source = PlantUmlAssert.documentPour(projet.entities().toArray(new UmlEntity[0]));

        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void aliasDesCobayesDuProjet_sontTousUniques() throws Exception {
        UmlProject projet = ProjectScanner.scan(Paths.get("target", "test-classes")).projet();
        String source = PlantUmlAssert.documentPour(projet.entities().toArray(new UmlEntity[0]));

        List<String> alias = PlantUmlAssert.aliasDeclares(source);

        assertTrue(alias.size() == projet.entities().size(),
            "Chaque entité scannée doit donner une déclaration de classe : "
                + alias.size() + " alias pour " + projet.entities().size() + " entités.");
        assertTrue(alias.stream().distinct().count() == alias.size(),
            "Des alias en double fusionneraient des classes distinctes : " + alias);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.svg", "a.SVG", "a.Svg", "a.png", "a.PNG", "a.pNg",
        "MON.DIAGRAMME.SVG"})
    void builder_accepteLesExtensionsQuelleQueSoitLaCasse(String nom, @TempDir Path tmp) {
        assertDoesNotThrow(
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile(nom).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.jpg", "a.pdf", "a.txt", "diagramme", "svg", "a.svg.bak", "a.s vg"})
    void builder_refuseToujoursLesFormatsNonSupportes(String nom, @TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile(nom));
    }

    @Test
    void builder_nommeLeFichierFautifDansSonMessage(@TempDir Path tmp) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile("rapport.pdf"));

        assertTrue(e.getMessage().contains("rapport.pdf"), e.getMessage());
    }

    @ParameterizedTest
    @CsvSource({"a.svg, SVG", "A.SVG, SVG", "a.Svg, SVG", "a.png, PNG", "A.PNG, PNG", "a.pNg, PNG"})
    void toutNomAccepteParLeBuilder_estRenduDansLeFormatDeSonExtension(
        String nom, String formatAttendu, @TempDir Path tmp) throws Exception {
        UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile(nom).build();

        Path sortie = tmp.resolve(nom);
        PlantUmlWriter.write(UmlProject.given(Set.of(EntityExtractor.extract(Cobaye.class))),
            sortie.toString());

        if (formatAttendu.equals("SVG")) {
            assertTrue(Files.readString(sortie, StandardCharsets.UTF_8).contains("<svg"),
                nom + " doit produire du SVG.");
        } else {
            byte[] entete = Arrays.copyOf(Files.readAllBytes(sortie), ENTETE_PNG.length);
            assertArrayEquals(ENTETE_PNG, entete, nom + " doit produire du PNG.");
        }
    }

    @Test
    void generationCompleteAvecExtensionEnMajuscules_produitBienDuSvg(@TempDir Path tmp)
        throws Exception {
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/banque/Compte.java", """
            package com.banque;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Compte {}
            """);
        Path sortie = tmp.resolve("ARCHITECTURE.SVG");

        UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(sortie.toString())
            .build()
            .generate();

        String contenu = Files.readString(sortie, StandardCharsets.UTF_8);
        assertTrue(contenu.contains("<svg"), "Le fichier livré doit être du SVG, pas du PNG.");
        assertTrue(contenu.contains("Compte"));
    }

    @Test
    void supporte_refuseUnNomNull() {
        assertThrows(NullPointerException.class, () -> PlantUmlWriter.supporte(null));
    }

    @UMLClass(name = "Cobaye")
    static class Cobaye {}

    @Test
    void builder_refuseUnNomDeFichierNull(@TempDir Path tmp) {
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile(null));
    }

    @Test
    void builder_refuseUnCheminSourceNull() {
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().sourceDirectory(null));
    }

    @Test
    void builder_refuseUnFichierCommeDossierSource(@TempDir Path tmp) throws Exception {
        Path fichier = tmp.resolve("pas-un-dossier.txt");
        Files.writeString(fichier, "x");

        assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory(fichier.toString()));
    }

    @Test
    void builder_accepteLesDeuxFormatsSupportes(@TempDir Path tmp) {
        assertDoesNotThrow(() -> UMLGenerator.builder()
            .sourceDirectory(tmp.toString()).outputFile("a.svg").build());
        assertDoesNotThrow(() -> UMLGenerator.builder()
            .sourceDirectory(tmp.toString()).outputFile("a.png").build());
    }

    @Test
    void builder_estReutilisablePourPlusieursGenerateurs(@TempDir Path tmp) {
        UMLGenerator.Builder builder = UMLGenerator.builder().sourceDirectory(tmp.toString());

        assertDoesNotThrow(() -> {
            builder.outputFile("a.svg").build();
            builder.outputFile("b.png").build();
        });
    }
}
