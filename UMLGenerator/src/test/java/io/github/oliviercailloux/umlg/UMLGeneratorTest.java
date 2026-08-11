package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// La façade vue de l'extérieur : ce que le builder accepte, ce qu'il refuse, et ce qu'une
// génération dépose réellement sur le disque.
//
// Chaque refus est vérifié au bon appel : le dossier invalide échoue dès sourceDirectory(),
// l'extension invalide dès outputFile(), l'absence de dossier source et les incohérences entre
// deux réglages seulement à build(). Ce n'est pas un détail : c'est ce qui donne un message
// pointant sur la ligne fautive.
//
// POURQUOI CE FICHIER NE SCANNE PLUS target/test-classes
//
// Il le faisait quatre fois, et pesait à lui seul un tiers du temps de la suite pour vérifier
// qu'une façade transmet bien ses paramètres. Les centaines de cobayes des autres tests ne
// prouvaient rien de plus ici qu'une seule classe annotée, copiée dans un répertoire à elle. Ce
// que target/test-classes protège — qu'aucun cobaye ajouté demain ne casse le rendu de
// l'ensemble — est l'affaire d'UMLGeneratorIntegrationTest, qui le fait une fois pour toutes.
class UMLGeneratorTest {

    // Le marqueur du thème de la bibliothèque dans la source engendrée : il n'y figure que sous
    // UmlStyle.UMLG. C'est ce qui permet de vérifier que le style demandé est bien appliqué, et
    // pas seulement accepté.
    private static final String MARQUEUR_DU_THEME_UMLG = "skinparam ClassBorderColor #33526E";

    // La seule classe que les générations de ce fichier ont à lire.
    @UMLClass(name = "CobayeDeLaFacade", strategy = UMLVisibility.PUBLIC)
    static class CobayeDeLaFacade {
        public String nom;
    }

    // Un répertoire de classes compilées qui ne contient que le cobaye ci-dessus : son .class est
    // recopié depuis target/test-classes, à sa place dans l'arborescence des paquetages. Le scan y
    // trouve une classe et une seule, ce qui rend chaque génération de ce fichier quasi
    // instantanée.
    private static Path unRepertoireDUneClasse(Path tmp) throws IOException {
        String chemin = CobayeDeLaFacade.class.getName().replace('.', '/') + ".class";
        Path racine = tmp.resolve("classes");
        Path destination = racine.resolve(chemin);
        Files.createDirectories(destination.getParent());
        Files.copy(Path.of("target", "test-classes").resolve(chemin), destination);
        return racine;
    }

    // Le builder d'une génération sur ce répertoire.
    private static UMLGenerator.Builder surLeCobaye(Path tmp) throws IOException {
        return UMLGenerator.builder().sourceDirectory(unRepertoireDUneClasse(tmp).toString());
    }

    // --- Ce que le builder refuse, et quand ---

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
        // Le style a un défaut, mais « null » n'en est pas un : ce serait un oubli, et le laisser
        // passer ferait échouer le rendu bien plus loin, sans dire pourquoi.
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).style(null));
    }

    // --- Les réglages qui se contredisaient en silence ---

    @Test
    void unFichierEtUnDossierDeSortie_sontRefusesEnsemble(@TempDir Path tmp) {
        // generate() tranchait par « if (outputDirectory != null) » : le fichier nommé n'était
        // jamais écrit, et rien ne le disait. L'utilisateur cherchait « archi.png » là où il
        // l'avait demandé, et ne l'y trouvait pas.
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
        // outputFormat n'est lu que dans la branche dossier : « .outputFile("a.png") » suivi de
        // « .outputFormat("svg") » livrait du PNG à qui venait de demander du SVG.
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> UMLGenerator.builder()
                .sourceDirectory(tmp.toString())
                .outputFile("a.png")
                .outputFormat("svg")
                .build());

        assertTrue(e.getMessage().contains("svg"), e.getMessage());
    }

    @Test
    void unFormatDemandeAvecUnDossier_estApplique(@TempDir Path tmp) throws IOException {
        // Le contrôle ne doit pas se refermer sur l'usage légitime, qui est celui du plugin Maven :
        // un dossier, et le format des images qu'on y dépose.
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
        // outputFileName porte un défaut — « diagramme_architecture.svg » —, et deux défauts ne se
        // contredisent pas. Seule une demande explicite entre en conflit avec une autre, faute de
        // quoi le mode dossier serait devenu inutilisable.
        Path dossier = tmp.resolve("diagrammes");

        RapportDeGeneration rapport = surLeCobaye(tmp)
            .outputDirectory(dossier.toString())
            .build()
            .generate();

        assertEquals(List.of(dossier.resolve("projet.svg")), rapport.fichiers());
    }

    @Test
    void desVuesEnModeFichierUnique_sontSignalees(@TempDir Path tmp) throws IOException {
        // Un projet qui déclare des vues et sort en fichier unique les perd toutes, sans un mot.
        // Rien, dans « .outputFile("archi.svg") », ne dit ce que le fichier de règles a demandé
        // par ailleurs : l'avertissement est le seul endroit où les deux se rencontrent.
        Path source = unRepertoireDUneClasse(tmp);
        Files.writeString(source.resolve("umlg.rules"), "views by package\n",
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
        // Le pendant du test précédent : en mode dossier, elles sont toutes écrites, et il n'y a
        // rien à signaler.
        Path source = unRepertoireDUneClasse(tmp);
        Files.writeString(source.resolve("umlg.rules"), "views by package\n",
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

    // --- Ce qu'une génération dépose vraiment ---

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
        // Le test qui existait ne vérifiait que l'acceptation du paramètre : passer UmlStyle.UMLG
        // au builder ne prouvait rien de ce qui est dessiné. Le style ne se voit que dans le
        // document engendré, et c'est là qu'il faut le lire.
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

    // --- keepPrevious, du builder jusqu'au disque ---

    @Test
    void keepPrevious_ecritLaGenerationSuivanteACote(@TempDir Path tmp) throws IOException {
        // Le comportement était testé un étage plus bas, mais son câblage builder → generate() ne
        // l'était pas : inverser le booléen en chemin n'aurait fait échouer aucun test.
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

    // --- classpath : l'API dont se sert le plugin Maven ---

    @Test
    void sansClasspath_uneClasseDontLeParentVitDansUnJar_estIllisible(@TempDir Path tmp)
        throws Exception {
        Path classes = unProjetDontLeParentEstDansUnJar(tmp);
        List<String> dits = new ArrayList<>();

        RuntimeException e;
        try (Avertissements.Canal canal = Avertissements.versLeCanal(dits::add)) {
            e = assertThrows(RuntimeException.class, () -> UMLGenerator.builder()
                .sourceDirectory(classes.toString())
                .outputFile(tmp.resolve("archi.svg").toString())
                .build()
                .generate());
        }

        // La classe n'a pas pu être liée : elle n'entre pas dans le modèle, et le projet se
        // retrouve sans aucune boîte. C'est le scénario exact que le classpath répare.
        assertTrue(dits.stream().anyMatch(a -> a.contains("appli.Fille")),
            "L'échec de chargement doit être dit, et nommer la classe : " + dits);
        assertTrue(e.getMessage().contains("nothing to draw"), e.getMessage());
    }

    @Test
    void avecLeClasspath_uneClasseDontLeParentVitDansUnJar_estDessinee(@TempDir Path tmp)
        throws Exception {
        // C'est l'API que le plugin Maven emploie, et que personne n'exerçait : il tourne dans la
        // JVM de Maven, et sans elle toute classe héritant d'une bibliothèque tierce disparaîtrait
        // du diagramme des projets réels — ceux dont le diagramme sert le plus.
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

    // Un projet d'une classe annotée dont le parent est ailleurs : compilé d'un bloc, puis le
    // parent est déplacé dans un jar posé à côté. Le répertoire analysé ne contient donc plus que
    // la fille, exactement comme le target/classes d'un projet qui dépend d'une bibliothèque.
    private static Path unProjetDontLeParentEstDansUnJar(Path tmp) throws Exception {
        Path classes = compiler(tmp,
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

    // Écrit des sources et les compile pour de bon : les arguments vont par deux, un chemin
    // relatif puis le code. Le répertoire des .class est rendu.
    private static Path compiler(Path tmp, String... cheminsEtCodes) throws IOException {
        Path sources = tmp.resolve("src");
        Path classes = tmp.resolve("compilees");
        Files.createDirectories(classes);

        List<File> fichiers = new ArrayList<>();
        for (int i = 0; i < cheminsEtCodes.length; i += 2) {
            Path fichier = sources.resolve(cheminsEtCodes[i]);
            Files.createDirectories(fichier.getParent());
            Files.writeString(fichier, cheminsEtCodes[i + 1], StandardCharsets.UTF_8);
            fichiers.add(fichier.toFile());
        }

        JavaCompiler compilateur = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager gestionnaire =
            compilateur.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            // -proc:none : ce test mesure le chargement par réflexion, pas le processeur
            // d'annotations, dont UmlgProcessorTest s'occupe.
            boolean ok = compilateur.getTask(null, gestionnaire, null,
                List.of("-proc:none", "-classpath", System.getProperty("java.class.path"),
                    "-d", classes.toString()),
                null, gestionnaire.getJavaFileObjects(fichiers.toArray(new File[0]))).call();
            assertTrue(ok, "Le cobaye n'a pas compilé.");
        }
        return classes;
    }

    // --- La ligne de commande ---

    @Test
    void ligneDeCommande_avecUnNomSansExtension_ecritUnDossierDeVues(@TempDir Path tmp)
        throws Exception {
        // Le trou que ce test protège : outputDirectory existait dans le builder, et la ligne de
        // commande n'y donnait pas accès — la fonctionnalité principale était inatteignable pour
        // qui n'écrivait pas sa propre classe de lancement.
        Path sortie = tmp.resolve("diagrammes");

        UMLGenerator.main(
            new String[] {unRepertoireDUneClasse(tmp).toString(), sortie.toString()});

        assertTrue(Files.isDirectory(sortie), "Un nom sans extension demande un dossier.");
        assertTrue(Files.exists(sortie.resolve("projet.svg")), "La vue intégrale y est écrite.");
    }

    @Test
    void ligneDeCommande_avecUneExtension_ecritCeSeulFichier(@TempDir Path tmp) throws Exception {
        // Personne n'appelle « architecture.svg » un répertoire : l'extension suffit à dire ce
        // qu'on veut, et évite une option de plus.
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

    // OÙ DÉPOSER LA LISTE DES CLASSES IGNORÉES
    //
    // Un nom de fichier nu n'a pas de parent, et ce n'est pas la même chose que n'avoir pas de
    // destination : c'est le répertoire courant. Confondre les deux privait de sa liste
    // l'invocation par défaut — « umlg target/classes » — ainsi que toute sortie nommée sans
    // dossier, tandis que le même appel préfixé de « ./ » l'obtenait. Deux commandes qui
    // déposent le fichier au même endroit doivent se comporter pareil.

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
