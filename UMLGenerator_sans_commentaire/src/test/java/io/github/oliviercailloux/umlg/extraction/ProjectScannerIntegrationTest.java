package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScannerIntegrationTest {

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
                List.of("-proc:none", "-classpath", cheminDeCompilation(), "-d",
                    sortie.toString()),
                null,
                gestionnaire.getJavaFileObjects(fichier.toFile())).call();

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

    private static Set<String> noms(UmlProject projet) {
        return projet.entities().stream().map(UmlEntity::name).collect(Collectors.toSet());
    }

    @Test
    void classeDansPackageImbrique_estDetecteeAvecSonNomSimple(@TempDir Path tmp) throws Exception {
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

        UmlProject projet = ProjectScanner.scan(sortie).projet();

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

        UmlProject projet = ProjectScanner.scan(sortie).projet();

        assertEquals(Set.of("Annotee"), noms(projet),
            "Seules les classes portant @UMLClass entrent dans le diagramme.");
    }

    @Test
    void classeInterneAnnotee_estDetectee(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Externe.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            public class Externe {
                @UMLClass(name = "Interne")
                public static class Interne {}
            }
            """);

        UmlProject projet = ProjectScanner.scan(sortie).projet();

        assertEquals(Set.of("Interne"), noms(projet));
    }

    @Test
    void fichiersNonClasse_sontIgnores(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Seule.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Seule {}
            """);
        Files.writeString(sortie.resolve("application.properties"), "cle=valeur");
        Files.createDirectories(sortie.resolve("META-INF"));
        Files.writeString(sortie.resolve("META-INF/MANIFEST.MF"), "Manifest-Version : 1.0");

        UmlProject projet = ProjectScanner.scan(sortie).projet();

        assertEquals(Set.of("Seule"), noms(projet));
    }

    @Test
    void dossierSansClasseAnnotee_estRefuseAvecUnMessageExplicite(@TempDir Path tmp)
        throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Rien.java", """
            package com.exemple;

            public class Rien {}
            """);

        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(sortie).projet());

        assertTrue(e.getMessage().contains("No class carries @UMLClass"), e.getMessage());
        assertTrue(e.getMessage().contains("Rien"),
            "Le message doit nommer les classes lues : " + e.getMessage());
    }

    @Test
    void dossierVide_estRefuse(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(tmp).projet());
    }

    @Test
    void fichierClasseIllisible_estIgnoreSansArreterLeScan(@TempDir Path tmp)
        throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Bonne.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Bonne {}
            """);
        Files.createDirectories(sortie.resolve("com/exemple"));
        Files.write(sortie.resolve("com/exemple/Corrompue.class"),
            new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0, 1, 2, 3});

        UmlProject projet = ProjectScanner.scan(sortie).projet();

        assertEquals(1, projet.entities().size());
        assertEquals("com.exemple.Bonne",
            projet.entities().iterator().next().qualifiedName());
    }

    @Test
    void toutesLesClassesIllisibles_neDonnentPasUnDiagrammeVideEnSilence(@TempDir Path tmp)
        throws Exception {
        Path sortie = tmp.resolve("classes");
        Files.createDirectories(sortie.resolve("com/exemple"));
        Files.write(sortie.resolve("com/exemple/Corrompue.class"),
            new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0, 1, 2, 3});

        assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(sortie).projet());
    }

    @Test
    void scanRepete_donneLeMemeResultat(@TempDir Path tmp) throws Exception {
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

        assertEquals(ProjectScanner.scan(sortie).projet().entities(),
            ProjectScanner.scan(sortie).projet().entities());
        assertEquals(Set.of("A", "B"), noms(ProjectScanner.scan(sortie).projet()));
    }

    @Test
    void annotationInvalideSurUneClasseScannee_remonteAuScan(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/MauvaiseCouleur.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass(color = "rouge-vif")
            public class MauvaiseCouleur {}
            """);

        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(sortie).projet());

        assertTrue(e.getMessage().contains("rouge-vif"), e.getMessage());
    }

    @Test
    void fichierDeRegles_estAppliqueAuProjetScanne(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Clavier.java", """
            package com.exemple;

            import com.google.common.collect.ImmutableList;
            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Clavier {
                public ImmutableList<String> touches;
            }
            """);
        Files.writeString(sortie.resolve("umlg.json"), """
            {
              "_": "Guava n'apprend rien au lecteur du diagramme.",
              "types": { "ImmutableList": "List" }
            }
            """, StandardCharsets.UTF_8);

        UmlEntity clavier = ProjectScanner.scan(sortie).projet().entities().iterator().next();

        assertEquals("List<String>", clavier.fields().get(0).type(),
            "La règle du projet doit atteindre les boîtes : " + clavier.fields());
    }

    @Test
    void sansFichierDeRegles_leScanDecritLeCodeTelQuilEst(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Clavier.java", """
            package com.exemple;

            import com.google.common.collect.ImmutableList;
            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Clavier {
                public ImmutableList<String> touches;
            }
            """);

        UmlEntity clavier = ProjectScanner.scan(sortie).projet().entities().iterator().next();

        assertEquals("ImmutableList<String>", clavier.fields().get(0).type());
    }

    @Test
    void projetCompileSansParameters_neMontreQueLesTypes(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Vol.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Vol {
                public void reporter(String nouvelleHeure, int minutes) {}
            }
            """);

        UmlEntity vol = ProjectScanner.scan(sortie).projet().entities().iterator().next();
        UmlMethod reporter = vol.methods().get(0);

        assertEquals(List.of("String", "int"),
            reporter.parameters().stream().map(UmlParameter::type).toList());
        assertTrue(reporter.parameters().stream().noneMatch(UmlParameter::nomme),
            "Aucun « arg0 » ne doit atteindre le dessin : " + reporter.parameters());
    }

    @Test
    void sansFichierDeRegles_lesReglagesSontCeuxParDefaut(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Facture.java", UNE_BOITE);

        assertEquals(UmlDiagram.defaut(), ProjectScanner.scan(sortie).projet().diagram());
    }

    @Test
    void titreEtSensDeLecture_sontLusDansLeFichierDeRegles(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Facture.java", UNE_BOITE);
        Files.writeString(sortie.resolve("umlg.json"), """
            {
              "title": "Architecture du module Facturation",
              "direction": "left-to-right",
              "showEmptyMembers": true
            }
            """, StandardCharsets.UTF_8);

        UmlDiagram diagramme = ProjectScanner.scan(sortie).projet().diagram();

        assertEquals("Architecture du module Facturation", diagramme.title());
        assertEquals(UMLFlow.LEFT_TO_RIGHT, diagramme.direction());
        assertFalse(diagramme.hideEmptyMembers());
    }

    @Test
    void fichierDeReglesFautif_arreteLeScan(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Facture.java", UNE_BOITE);
        Files.writeString(sortie.resolve("umlg.json"), "{ \"title\": \"\" }\n",
            StandardCharsets.UTF_8);

        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(sortie).projet());

        assertTrue(e.getMessage().contains("the title cannot be empty"), e.getMessage());
    }

    @Test
    void unProjetModulaire_neFaitPasEchouerLeScan(@TempDir Path tmp) throws Exception {
        Path sources = tmp.resolve("src");
        Path sortie = tmp.resolve("classes");
        compiler(sources, sortie, "com/exemple/Facture.java", UNE_BOITE);

        Path descripteur = sources.resolve("module-info.java");
        Files.writeString(descripteur, "module cobaye {\n}\n", StandardCharsets.UTF_8);
        JavaCompiler compilateur = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager gestionnaire =
            compilateur.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            assertTrue(compilateur.getTask(null, gestionnaire, null,
                List.of("-proc:none", "-d", sortie.toString()), null,
                gestionnaire.getJavaFileObjects(descripteur.toFile())).call(),
                "Le descripteur de module doit compiler.");
        }
        assertTrue(Files.exists(sortie.resolve("module-info.class")),
            "Le cas n'est pas reproduit si le descripteur n'est pas là.");

        UmlProject projet = ProjectScanner.scan(sortie).projet();

        assertEquals(Set.of("Facture"), noms(projet),
            "Le module-info est ignoré, et les classes annotées sont bien lues.");
    }

    @Test
    void lOrdreDesBoites_neDependPasDuSystemeDeFichiers(@TempDir Path tmp) throws Exception {
        Path sources = tmp.resolve("src");
        Path sortie = tmp.resolve("classes");
        for (String nom : List.of("Zebre", "Alpha", "Milieu")) {
            compiler(sources, sortie, "com/exemple/" + nom + ".java", """
                package com.exemple;

                import io.github.oliviercailloux.umlg.annotation.UMLClass;

                @UMLClass
                public class NOM {}
                """.replace("NOM", nom));
        }

        List<String> lus = ProjectScanner.scan(sortie).projet().entities().stream()
            .map(UmlEntity::name).toList();

        assertEquals(List.of("Alpha", "Milieu", "Zebre"), lus,
            "Les boîtes doivent être rendues dans un ordre stable, ici l'alphabétique.");
    }

    private static final String UNE_BOITE = """
        package com.exemple;

        import io.github.oliviercailloux.umlg.annotation.UMLClass;

        @UMLClass
        public class Facture {}
        """;

}
