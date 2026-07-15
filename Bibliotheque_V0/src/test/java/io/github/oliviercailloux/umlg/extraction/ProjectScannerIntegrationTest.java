package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests d'intégration du scanner sur de vrais {@code .class} compilés à la volée par
 * javac dans un dossier temporaire.
 *
 * <p>Les tests existants scannent {@code target/test-classes}, ce qui les rend
 * dépendants de l'ensemble des cobayes du projet et incapables de couvrir la traduction
 * chemin → nom qualifié (packages imbriqués, classes internes) ou les fichiers illisibles.
 * Ici, chaque test fabrique exactement l'arborescence dont il a besoin.
 */
class ProjectScannerIntegrationTest {

    /** Compile {@code code} (chemin relatif au package près) vers {@code sortie}. */
    private static void compiler(Path racineSources, Path sortie, String cheminRelatif, String code)
        throws IOException {
        Path fichier = racineSources.resolve(cheminRelatif);
        Files.createDirectories(fichier.getParent());
        Files.writeString(fichier, code, StandardCharsets.UTF_8);

        JavaCompiler compilateur = ToolProvider.getSystemJavaCompiler();
        if (compilateur == null) {
            throw new IllegalStateException("Ces tests exigent un JDK (javac absent).");
        }
        Files.createDirectories(sortie);

        try (StandardJavaFileManager gestionnaire =
            compilateur.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {

            boolean ok = compilateur.getTask(null, gestionnaire, null,
                List.of("-classpath", System.getProperty("java.class.path"),
                    "-d", sortie.toString()),
                null,
                gestionnaire.getJavaFileObjects(fichier.toFile())).call();

            if (!ok) {
                throw new IllegalStateException("Le cobaye n'a pas compilé :\n" + code);
            }
        }
    }

    private static Set<String> noms(UmlProject projet) {
        return projet.entities().stream().map(UmlEntity::name).collect(Collectors.toSet());
    }

    @Test
    void classeDansPackageImbrique_estDetecteeAvecSonNomSimple(@TempDir Path tmp) throws Exception {
        // Vérifie la traduction « chemin de fichier → nom qualifié » : sans elle, le
        // classLoader ne retrouverait pas la classe et le scan lèverait IllegalStateException.
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/domaine/Facture.java", """
            package com.exemple.domaine;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Facture {
                public int montant;
                public void emettre() {}
            }
            """);

        UmlProject projet = ProjectScanner.scan(sortie);

        assertEquals(Set.of("Facture"), noms(projet));
        UmlEntity facture = projet.entities().iterator().next();
        assertTrue(facture.fields().stream().anyMatch(f -> f.name().equals("montant")));
        assertTrue(facture.methods().stream().anyMatch(m -> m.name().equals("emettre")));
    }

    @Test
    void classeNonAnnotee_estIgnoree(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Annotee.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Annotee {}
            """);
        compiler(tmp.resolve("src"), sortie, "com/exemple/Ordinaire.java", """
            package com.exemple;

            public class Ordinaire {
                public int champ;
            }
            """);

        UmlProject projet = ProjectScanner.scan(sortie);

        assertEquals(Set.of("Annotee"), noms(projet),
            "Seules les classes portant @UMLClass entrent dans le diagramme.");
    }

    @Test
    void classeInterneAnnotee_estDetectee(@TempDir Path tmp) throws Exception {
        // Les classes internes produisent un fichier « Externe$Interne.class » : le nom
        // qualifié doit conserver le '$' pour rester chargeable.
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Externe.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            public class Externe {
                @UMLClass(name = "Interne")
                public static class Interne {}
            }
            """);

        UmlProject projet = ProjectScanner.scan(sortie);

        assertEquals(Set.of("Interne"), noms(projet));
    }

    @Test
    void fichiersNonClasse_sontIgnores(@TempDir Path tmp) throws Exception {
        // Un dossier de build réel contient aussi des ressources (.properties, .txt, .xml).
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Seule.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Seule {}
            """);
        Files.writeString(sortie.resolve("application.properties"), "cle=valeur");
        Files.createDirectories(sortie.resolve("META-INF"));
        Files.writeString(sortie.resolve("META-INF/MANIFEST.MF"), "Manifest-Version: 1.0");

        UmlProject projet = ProjectScanner.scan(sortie);

        assertEquals(Set.of("Seule"), noms(projet));
    }

    @Test
    void dossierSansClasseAnnotee_estRefuseAvecUnMessageExplicite(@TempDir Path tmp) throws Exception {
        // UmlProject exige au moins une entité : scanner un projet où personne n'a annoté
        // doit produire une erreur compréhensible, pas un diagramme vide.
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Rien.java", """
            package com.exemple;

            public class Rien {}
            """);

        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(sortie));

        assertTrue(e.getMessage().contains("au moins une classe annotée"), e.getMessage());
    }

    @Test
    void dossierVide_estRefuse(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(tmp));
    }

    @Test
    void fichierClasseIllisible_produitUneErreurQuiNommeLaClasse(@TempDir Path tmp) throws Exception {
        // Un .class corrompu ou compilé pour une JVM plus récente lève une LinkageError au
        // chargement. Le scan doit l'attraper et dire QUEL fichier pose problème.
        Path sortie = tmp.resolve("classes");
        Files.createDirectories(sortie.resolve("com/exemple"));
        Files.write(sortie.resolve("com/exemple/Corrompue.class"),
            new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0, 1, 2, 3});

        IllegalStateException e =
            assertThrows(IllegalStateException.class, () -> ProjectScanner.scan(sortie));

        assertTrue(e.getMessage().contains("com.exemple.Corrompue"),
            "Le message doit nommer la classe fautive : " + e.getMessage());
    }

    @Test
    void scanRepete_donneLeMemeResultat(@TempDir Path tmp) throws Exception {
        // Le diagramme ne doit pas dépendre de l'ordre de parcours du système de fichiers.
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/A.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class A {}
            """);
        compiler(tmp.resolve("src"), sortie, "com/exemple/B.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class B {}
            """);

        assertEquals(ProjectScanner.scan(sortie).entities(), ProjectScanner.scan(sortie).entities());
        assertEquals(Set.of("A", "B"), noms(ProjectScanner.scan(sortie)));
    }

    @Test
    void annotationInvalideSurUneClasseScannee_remonteAuScan(@TempDir Path tmp) throws Exception {
        // La validation de @UMLClass (couleur hexadécimale) doit s'appliquer aussi quand la
        // classe est découverte par le scan, et pas seulement en appel direct à extract().
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/MauvaiseCouleur.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass(color = "rouge-vif")
            public class MauvaiseCouleur {}
            """);

        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(sortie));

        assertTrue(e.getMessage().contains("rouge-vif"), e.getMessage());
    }
}
