package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.Avertissements;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Le scan sur de vraies classes compilées, et non sur celles du test.
//
// Le procédé est le même dans chaque test : on écrit un fichier .java dans un répertoire
// temporaire, on le compile pour de bon avec javac, puis on scanne le répertoire de sortie.
// C'est le seul moyen d'éprouver ce qui n'existe qu'à ce niveau — la traduction chemin → nom
// qualifié, les paquetages imbriqués, les fichiers illisibles, les ressources non .class.
class ProjectScannerIntegrationTest {

    // Écrit le code source donné puis le compile vers le répertoire de sortie.
    private static void compiler(Path racineSources, Path sortie, String cheminRelatif, String code)
        throws IOException {
        Path fichier = racineSources.resolve(cheminRelatif);
        // Les répertoires de paquetage n'existent pas encore : à créer avant d'écrire.
        Files.createDirectories(fichier.getParent());
        Files.writeString(fichier, code, StandardCharsets.UTF_8);

        // Le compilateur du JDK, accessible depuis le programme lui-même. Absent d'un simple
        // JRE, d'où la vérification qui suit.
        JavaCompiler compilateur = ToolProvider.getSystemJavaCompiler();
        if (compilateur == null) {
            throw new IllegalStateException("Ces tests exigent un JDK (javac absent).");
        }
        Files.createDirectories(sortie);

        try (StandardJavaFileManager gestionnaire =
            compilateur.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {

            // -classpath : le cobaye importe nos annotations, il faut donc les lui rendre
            // visibles. -d : où déposer les .class, c'est-à-dire ce que le scan lira ensuite.
            //
            // -proc:none : le classpath contient nos propres classes, donc le
            // META-INF/services qui déclare UmlgProcessor ; sans cette option javac
            // l'installerait et refuserait les cobayes délibérément fautifs. Or c'est
            // précisément la validation d'exécution que ces tests mesurent — la seconde ligne
            // de défense, celle qui protège l'utilisateur compilant sans le processeur, et
            // celui qui construit le modèle par programme. Les vérifications du processeur,
            // elles, sont éprouvées par UmlgProcessorTest.
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

    // Le classpath du test lui-même, plus le module path : selon la façon dont Maven lance les
    // tests, nos classes se trouvent sur l'un ou sur l'autre. On concatène les deux plutôt que
    // de deviner.
    private static String cheminDeCompilation() {
        return Stream.of(System.getProperty("java.class.path"),
                System.getProperty("jdk.module.path"))
            .filter(chemin -> chemin != null && !chemin.isBlank())
            .collect(Collectors.joining(File.pathSeparator));
    }

    // Raccourci de lecture : les noms affichés des entités, pour comparer d'un coup avec Set.of.
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

        UmlProject projet = ProjectScanner.scan(sortie).projet();

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
        Files.writeString(sortie.resolve("META-INF/MANIFEST.MF"), "Manifest-Version : 1.0");

        UmlProject projet = ProjectScanner.scan(sortie).projet();

        assertEquals(Set.of("Seule"), noms(projet));
    }

    @Test
    void dossierSansClasseAnnotee_estRefuseAvecUnMessageExplicite(@TempDir Path tmp)
        throws Exception {
        // UmlProject exige au moins une entité : scanner un projet où personne n'a annoté
        // doit produire une erreur compréhensible, pas un diagramme vide.
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Rien.java", """
            package com.exemple;

            public class Rien {}
            """);

        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(sortie).projet());

        // Le message ne se contente pas de constater : le scan vient de lire cette classe, il
        // connaît son nom, et c'est celui-là qu'il faut annoter.
        assertTrue(e.getMessage().contains("No class carries @UMLClass"), e.getMessage());
        assertTrue(e.getMessage().contains("Rien"),
            "Le message doit nommer les classes lues : " + e.getMessage());
    }

    // Répertoire existant mais vide : le refus vient d'UmlProject, non du scan lui-même.
    @Test
    void dossierVide_estRefuse(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(tmp).projet());
    }

    @Test
    void fichierClasseIllisible_estIgnoreSansArreterLeScan(@TempDir Path tmp)
        throws Exception {
        // Un .class corrompu, compilé pour une JVM plus récente, ou dont une dépendance manque au
        // classpath, lève une LinkageError au chargement. Le scan doit passer son chemin plutôt
        // que d'avorter : la classe illisible n'est très probablement pas celle que le diagramme
        // décrit, et faire échouer toute la génération pour elle rendait l'outil inutilisable sur
        // un projet réel où une dépendance « provided » manque.
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Bonne.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Bonne {}
            """);
        Files.createDirectories(sortie.resolve("com/exemple"));
        // Les quatre premiers octets sont le nombre magique d'un .class (0xCAFEBABE) : le
        // fichier est donc reconnu comme tel, mais la suite ne veut rien dire.
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
        // Le cas limite du précédent : si la seule classe du répertoire est illisible, il ne
        // reste rien à dessiner. Le refus doit rester, sans quoi l'utilisateur recevrait un
        // diagramme vide sans savoir pourquoi.
        Path sortie = tmp.resolve("classes");
        Files.createDirectories(sortie.resolve("com/exemple"));
        Files.write(sortie.resolve("com/exemple/Corrompue.class"),
            new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0, 1, 2, 3});

        assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(sortie).projet());
    }

    // --- Une dépendance absente du classpath analysé ---

    // Compile une classe annotée qui nomme un type déclaré dans le même fichier, puis retire le
    // .class de ce type : ce qui reste est exactement un projet dont une dépendance manque au
    // classpath analysé — le cas d'une dépendance « provided » oubliée. Une seconde classe, saine
    // et indépendante, sert à constater que le scan est allé jusqu'au bout.
    private static Path dependanceRetiree(Path tmp, String declarationDuChamp) throws IOException {
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Porteuse.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            class Absente {}

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Porteuse {
                public CHAMP
            }
            """.replace("CHAMP", declarationDuChamp));
        compiler(tmp.resolve("src"), sortie, "com/exemple/Saine.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Saine {}
            """);
        Files.delete(sortie.resolve("com/exemple/Absente.class"));
        return sortie;
    }

    // Ce que le scan a dit en chemin. Le canal est rétabli dans un finally : laissé en place, il
    // capterait les avertissements du test suivant, qui tourne sur le même fil.
    private static List<String> avertissementsDuScan(Path sortie) throws IOException {
        List<String> dits = new ArrayList<>();
        Avertissements.versLeCanal(dits::add);
        try {
            ProjectScanner.scan(sortie);
        } finally {
            Avertissements.retablirLeDefaut();
        }
        return dits;
    }

    // L'unique avertissement qui nomme la classe donnée.
    private static String avertissementSur(List<String> dits, String classe) {
        List<String> retenus = dits.stream().filter(m -> m.contains(classe)).toList();
        assertEquals(1, retenus.size(), "Un seul avertissement doit nommer " + classe + " : "
            + dits);
        return retenus.get(0);
    }

    @Test
    void typeAbsentDansUneSignatureGenerique_neFaitPasEchouerLeScan(@TempDir Path tmp)
        throws Exception {
        // getGenericType() sur « List<Absente> » ne lève pas NoClassDefFoundError mais
        // TypeNotPresentException, une RuntimeException qui échappait à la clause de capture : le
        // scan entier avortait, et le message ne nommait ni la classe, ni le fichier, ni le
        // remède. Le même répertoire avec un champ non générique passait, lui — la forme de la
        // signature décidait du sort du scan.
        Path sortie = dependanceRetiree(tmp, "java.util.List<Absente> liste;");

        UmlProject projet = ProjectScanner.scan(sortie).projet();

        assertEquals(Set.of("Saine"), noms(projet),
            "Le scan doit livrer les boîtes qu'il a su lire, et se passer de l'autre.");
    }

    @Test
    void typeAbsentDansUneSignatureNue_neFaitPasEchouerLeScan(@TempDir Path tmp) throws Exception {
        // Le pendant du précédent : ici la réflexion lève une LinkageError, déjà rattrapée.
        // Les deux formes doivent avoir le même sort, faute de quoi rien ne permet de prévoir
        // lequel des deux répertoires se dessinera.
        Path sortie = dependanceRetiree(tmp, "Absente champ;");

        UmlProject projet = ProjectScanner.scan(sortie).projet();

        assertEquals(Set.of("Saine"), noms(projet));
    }

    @Test
    void uneClasseChargeeMaisIllisible_estSignaleeSansPretendreQuElleNAPasCharge(@TempDir Path tmp)
        throws Exception {
        // Le message des classes illisibles dit « on ne peut pas savoir si elles portaient
        // @UMLClass, l'annotation ne se lit que sur une classe chargée ». Sur Porteuse, il serait
        // faux mot pour mot : elle s'est chargée, l'annotation a été lue, elle y était, et c'est
        // sa boîte qui est perdue. Le message doit donc être l'autre.
        Path sortie = dependanceRetiree(tmp, "java.util.List<Absente> liste;");

        String dit = avertissementSur(avertissementsDuScan(sortie), "com.exemple.Porteuse");

        assertTrue(dit.contains("loaded but could not be read"), dit);
        assertTrue(dit.contains("a type it names"), dit);
        assertFalse(dit.contains("only readable on a loaded class"),
            "La classe s'est chargée : ce reproche-là ne la concerne pas — " + dit);
    }

    @Test
    void uneClasseQuiNeChargePas_gardeLeMessageDesClassesIllisibles(@TempDir Path tmp)
        throws Exception {
        // L'autre moitié de la séparation : un .class que le chargeur refuse garde bien le
        // message qui nomme le doute sur @UMLClass, seul cas où ce doute existe.
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Saine.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Saine {}
            """);
        Files.write(sortie.resolve("com/exemple/Corrompue.class"),
            new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0, 1, 2, 3});

        String dit = avertissementSur(avertissementsDuScan(sortie), "com.exemple.Corrompue");

        assertTrue(dit.contains("could not be loaded"), dit);
        assertTrue(dit.contains("only readable on a loaded class"), dit);
    }

    @Test
    void toutesLesBoitesPerdues_neDonnentPasUnDiagrammeVideEnSilence(@TempDir Path tmp)
        throws Exception {
        // Le cas limite : si la seule classe annotée du répertoire est illisible à l'extraction,
        // il ne reste rien à dessiner. Le refus doit rester — mais précédé de l'avertissement qui
        // en donne la cause, sans quoi l'utilisateur ne lirait qu'un « aucune classe annotée »
        // incompréhensible sur un répertoire où il vient d'en écrire une.
        Path sortie = tmp.resolve("classes");
        compiler(tmp.resolve("src"), sortie, "com/exemple/Porteuse.java", """
            package com.exemple;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            class Absente {}

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Porteuse {
                public java.util.List<Absente> liste;
            }
            """);
        Files.delete(sortie.resolve("com/exemple/Absente.class"));

        List<String> dits = new ArrayList<>();
        Avertissements.versLeCanal(dits::add);
        try {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ProjectScanner.scan(sortie).projet());
            assertTrue(e.getMessage().contains("No class carries @UMLClass"), e.getMessage());
        } finally {
            Avertissements.retablirLeDefaut();
        }

        assertTrue(avertissementSur(dits, "com.exemple.Porteuse")
            .contains("loaded but could not be read"), dits.toString());
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

        assertEquals(ProjectScanner.scan(sortie).projet().entities(),
            ProjectScanner.scan(sortie).projet().entities());
        assertEquals(Set.of("A", "B"), noms(ProjectScanner.scan(sortie).projet()));
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
            assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(sortie).projet());

        assertTrue(e.getMessage().contains("rouge-vif"), e.getMessage());
    }

    // --- Le fichier de règles du projet ---

    @Test
    void fichierDeRegles_estAppliqueAuProjetScanne(@TempDir Path tmp) throws Exception {
        // Le fichier vit à la racine du répertoire analysé, à côté des .class — c'est-à-dire, pour
        // un projet Maven, dans src/main/resources, que la construction y recopie.
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

    // --- Les noms des paramètres, quand le projet n'a pas été compilé pour les garder ---

    @Test
    void projetCompileSansParameters_neMontreQueLesTypes(@TempDir Path tmp) throws Exception {
        // Les cobayes de ce test sont compilés sans l'option « -parameters » (voir compiler),
        // c'est-à-dire comme la plupart des projets. La réflexion ne rend alors que « arg0 »,
        // « arg1 »… et la bibliothèque doit s'en tenir aux types plutôt que d'écrire sur le
        // dessin des noms que personne n'a choisis.
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
        // Un module-info.class n'est pas une classe : le charger lève « access_flag ACC_MODULE
        // is set », et le scan entier échouait sur le premier projet modulaire rencontré. Aucun
        // cobaye n'en portait, et le défaut n'est apparu qu'en analysant un vrai projet.
        Path sources = tmp.resolve("src");
        Path sortie = tmp.resolve("classes");
        compiler(sources, sortie, "com/exemple/Facture.java", UNE_BOITE);

        // Le descripteur de module est compilé à part : il n'a besoin de rien, et sa seule
        // présence dans le répertoire suffit à reproduire le cas.
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
        // Files.walk rend les fichiers dans l'ordre du système de fichiers, qu'aucune norme ne
        // définit : sans tri, deux machines produiraient deux diagrammes différents. L'ordre des
        // boîtes gouverne la mise en page, et les vues y puisent l'ordre de leurs liens
        // invisibles — ce n'est donc pas une gêne théorique.
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

    // --- Les réglages d'ensemble, lus dans le fichier de règles ---

    // Une classe annotée, pour que le projet ne soit pas vide : les réglages d'ensemble règlent
    // le dessin, ils ne le peuplent pas.
    private static final String UNE_BOITE = """
        package com.exemple;

        import io.github.oliviercailloux.umlg.annotation.UMLClass;

        @UMLClass
        public class Facture {}
        """;

}
