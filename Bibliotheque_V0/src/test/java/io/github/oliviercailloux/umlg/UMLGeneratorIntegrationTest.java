package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

// La bibliothèque entière, du code annoté au fichier livré.
//
// Trois familles de tests :
//   - la chaîne complète : on écrit un vrai projet Java, on le compile, on le scanne, on lit le
//     SVG produit et on y vérifie que les décisions prises en amont (@UMLExclude, filtre
//     anti-bruit, stratégie) tiennent jusqu'à l'image ;
//   - un invariant global sur target/test-classes : quel que soit le cobaye qu'un futur test
//     ajoutera, l'ensemble doit continuer à produire un PlantUML valide et sans alias en
//     double ;
//   - l'accord entre le Builder et le writer sur les extensions : ce que l'un accepte, l'autre
//     doit savoir le rendre — un désaccord passé entre eux est à l'origine de ces tests.
class UMLGeneratorIntegrationTest {

    // Nombre magique d'un PNG : sert à vérifier le format réellement écrit.
    private static final byte[] ENTETE_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    // Même procédé que dans ProjectScannerIntegrationTest : écrire un .java, le compiler pour
    // de bon, et scanner le répertoire de sortie.
    private static void compiler(Path racineSources, Path sortie, String cheminRelatif, String code)
        throws IOException {
        Path fichier = racineSources.resolve(cheminRelatif);
        Files.createDirectories(fichier.getParent());
        Files.writeString(fichier, code, StandardCharsets.UTF_8);
        Files.createDirectories(sortie);

        JavaCompiler compilateur = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager gestionnaire =
            compilateur.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {

            // -proc:none : ces tests mesurent la chaîne scan puis rendu, pas le processeur
            // d'annotations. Le désactiver explicitement les rend indépendants de la façon dont
            // javac découvre les processeurs présents sur le classpath, et laisse la porte
            // ouverte à un futur cobaye délibérément fautif. UmlgProcessorTest s'occupe du
            // processeur.
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

    // --- Chaîne complète ---

    @Test
    void projetAnnote_produitUnSvgContenantLArchitecture(@TempDir Path tmp) throws Exception {
        // Le scénario nominal de la bibliothèque, de bout en bout.
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
        assertFalse(svg.contains("getSolde"), "Le filtre anti-bruit doit tenir jusqu'au SVG.");
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

        // generate() enrobe toute erreur : le message doit rendre le diagnostic possible.
        assertTrue(e.getMessage().contains(classes.toString()),
            "Le dossier source doit figurer dans le message : " + e.getMessage());
        assertTrue(e.getMessage().contains(sortie.toString()),
            "Le fichier de sortie doit figurer dans le message : " + e.getMessage());
        assertTrue(e.getCause() != null, "La cause d'origine doit être conservée.");
        assertFalse(Files.exists(sortie), "Aucun fichier ne doit être écrit en cas d'échec.");
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
        // Relancer le générateur sur un projet inchangé ne doit ni échouer ni produire un
        // diagramme différent.
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

    // --- Invariant global sur les cobayes du projet ---

    @Test
    void toutesLesClassesCobayesDuProjet_produisentUnDiagrammeValide() throws Exception {
        // Filet de sécurité : quel que soit le cobaye ajouté par un futur test, l'ensemble
        // de target/test-classes doit continuer à produire un PlantUML que le moteur accepte.
        // C'est ce test qui attrape les alias vides ou en collision, qu'aucun assert sur la
        // taille du fichier ne peut voir.
        UmlProject projet = ProjectScanner.scan(Paths.get("target", "test-classes"));

        String source = PlantUmlAssert.documentPour(projet.entities().toArray(new UmlEntity[0]));

        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void aliasDesCobayesDuProjet_sontTousUniques() throws Exception {
        UmlProject projet = ProjectScanner.scan(Paths.get("target", "test-classes"));
        String source = PlantUmlAssert.documentPour(projet.entities().toArray(new UmlEntity[0]));

        List<String> alias = PlantUmlAssert.aliasDeclares(source);

        assertTrue(alias.size() == projet.entities().size(),
            "Chaque entité scannée doit donner une déclaration de classe : "
                + alias.size() + " alias pour " + projet.entities().size() + " entités.");
        assertTrue(alias.stream().distinct().count() == alias.size(),
            "Des alias en double fusionneraient des classes distinctes : " + alias);
    }

    // --- Builder ---

    @ParameterizedTest
    @ValueSource(strings = {"a.svg", "a.SVG", "a.Svg", "a.png", "a.PNG", "a.pNg",
        "MON.DIAGRAMME.SVG"})
    void builder_accepteLesExtensionsQuelleQueSoitLaCasse(String nom, @TempDir Path tmp) {
        // Le Builder refusait « Diagramme.SVG » alors que PlantUmlWriter savait le rendre :
        // les deux comparaient l'extension chacun de leur côté, l'un sans normaliser la casse.
        assertDoesNotThrow(
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile(nom).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.jpg", "a.pdf", "a.txt", "diagramme", "svg", "a.svg.bak", "a.s vg"})
    void builder_refuseToujoursLesFormatsNonSupportes(String nom, @TempDir Path tmp) {
        // Le durcissement de la casse ne doit pas transformer le filtre en passoire.
        assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile(nom));
    }

    @Test
    void builder_nommeLeFichierFautifDansSonMessage(@TempDir Path tmp) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile("rapport.pdf"));

        assertTrue(e.getMessage().contains("rapport.pdf"), e.getMessage());
    }

    // @CsvSource : chaque ligne fournit deux arguments, le nom de fichier et le format attendu.
    @ParameterizedTest
    @CsvSource({"a.svg, SVG", "A.SVG, SVG", "a.Svg, SVG", "a.png, PNG", "A.PNG, PNG", "a.pNg, PNG"})
    void toutNomAccepteParLeBuilder_estRenduDansLeFormatDeSonExtension(
        String nom, String formatAttendu, @TempDir Path tmp) throws Exception {
        // Le vrai invariant, celui qui empêche les deux composants de redivorcer : ce que le
        // Builder laisse passer, le writer doit le rendre dans le format annoncé par
        // l'extension — casse comprise, et sans repli silencieux sur le PNG.
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
        // La chaîne complète, pas seulement la validation : « .SVG » traverse le Builder,
        // le scan et le writer.
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

    // Le builder n'est pas consommé par build() : on peut en tirer plusieurs générateurs, en ne
    // changeant que le fichier de sortie.
    @Test
    void builder_estReutilisablePourPlusieursGenerateurs(@TempDir Path tmp) {
        UMLGenerator.Builder builder = UMLGenerator.builder().sourceDirectory(tmp.toString());

        assertDoesNotThrow(() -> {
            builder.outputFile("a.svg").build();
            builder.outputFile("b.png").build();
        });
    }
}
