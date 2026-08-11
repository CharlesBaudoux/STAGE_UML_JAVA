package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D'un répertoire de {@code .class} aux fichiers {@code .puml} livrés.
 *
 * <p>CE QUE CE TEST COUVRE ET QUE LES AUTRES NE COUVRENT PAS
 *
 * <p>La chaîne complète était déjà éprouvée, mais en deux moitiés disjointes.
 * {@code UMLGeneratorIntegrationTest} compile un vrai projet et va jusqu'à l'image, sur un cobaye
 * d'une seule classe sans relation ni vue. {@code PlantUmlVuesEcritesTest} éprouve le dossier de
 * sortie — l'intégrale à la racine, les vues dans un sous-dossier, la source à côté de chaque
 * image — mais à partir d'un modèle construit à la main, sans jamais lire un {@code .class}.
 *
 * <p>Entre les deux restait la jointure : un projet compilé, dont le fichier de règles déclare des
 * vues, dont les classes portent de vraies relations, et dont on lit les {@code .puml} produits.
 * C'est là que vivent les décisions qui n'existent qu'une fois tout assemblé — la fusion des
 * associations réciproques, les liens de navigation d'une vue à l'autre, l'italique d'une
 * opération abstraite lue par réflexion.
 *
 * <p>POURQUOI LE .PUML, ET NON LE SVG
 *
 * <p>C'est le fichier qui se relit, se retouche et se versionne, et c'est celui dont un diff dit
 * quelque chose. Le SVG répond à « le dessin est-il sorti », le {@code .puml} à « la bibliothèque
 * a-t-elle écrit ce qu'il fallait » — la seule des deux questions dont la réponse se lise.
 */
class DuCodeCompileAuPumlTest {

    /**
     * Un projet complet en un seul fichier : une hiérarchie, une paire d'attributs réciproques,
     * une opération abstraite, et une classe que personne n'a annotée.
     *
     * <p>Tout tient dans un fichier parce que javac accepte plusieurs types de premier niveau
     * dès lors qu'un seul est public, et qu'une compilation suffit alors là où il en faudrait
     * quatre.
     */
    private static final String COBAYE = """
        package com.ecole;

        import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
        import io.github.oliviercailloux.umlg.annotation.UMLClass;
        import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
        import java.util.List;

        @UMLClass(strategy = UMLVisibility.PUBLIC)
        public abstract class Personne {
            public String nom;

            public abstract void valider();
        }

        @UMLClass(strategy = UMLVisibility.PUBLIC)
        class Eleve extends Personne {
            @UMLAssociation(targetCardinality = "0..*", targetRole = "cours")
            public List<Cours> cours;

            @Override
            public void valider() {}
        }

        @UMLClass(strategy = UMLVisibility.PUBLIC)
        class Cours {
            public String intitule;

            @UMLAssociation(targetCardinality = "1..*", targetRole = "inscrits")
            public List<Eleve> inscrits;
        }

        class Intendance {}
        """;

    /** Les règles du projet, telles que la construction les recopie à la racine des classes. */
    private static final String REGLES = """
        {
          "title": "École",
          "views": [ { "name": "Scolarite", "retain": ["Eleve", "Cours"] } ]
        }
        """;

    /**
     * Écrit le cobaye, le compile pour de bon, et pose le fichier de règles là où le scan le
     * cherche : à la racine du répertoire analysé.
     *
     * <p>Le même procédé que dans {@code UMLGeneratorIntegrationTest} et
     * {@code ProjectScannerIntegrationTest}, où il est déjà écrit deux fois. Le recopier une
     * troisième n'est pas satisfaisant, mais cette duplication-là ne peut pas mentir : si elle
     * dérive, les tests échouent bruyamment au lieu de mesurer autre chose en silence.
     *
     * @param tmp le répertoire du test
     * @return le répertoire des classes compilées, prêt à être analysé
     */
    private static Path projetCompile(Path tmp) throws IOException {
        Path sources = tmp.resolve("src");
        Path classes = tmp.resolve("classes");
        Path fichier = sources.resolve("com/ecole/Personne.java");
        Files.createDirectories(fichier.getParent());
        Files.writeString(fichier, COBAYE, StandardCharsets.UTF_8);
        Files.createDirectories(classes);

        JavaCompiler compilateur = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager gestionnaire =
            compilateur.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {

            boolean ok = compilateur.getTask(null, gestionnaire, null,
                List.of("-proc:none", "-classpath", cheminDeCompilation(),
                    "-d", classes.toString()),
                null, gestionnaire.getJavaFileObjects(fichier.toFile())).call();
            if (!ok) {
                throw new IllegalStateException("Le cobaye n'a pas compilé :\n" + COBAYE);
            }
        }
        Files.writeString(classes.resolve("umlg.json"), REGLES, StandardCharsets.UTF_8);
        return classes;
    }

    private static String cheminDeCompilation() {
        return Stream.of(System.getProperty("java.class.path"),
                System.getProperty("jdk.module.path"))
            .filter(chemin -> chemin != null && !chemin.isBlank())
            .collect(Collectors.joining(File.pathSeparator));
    }

    /** Engendre le dossier de diagrammes du cobaye, et rend la source de la vue d'ensemble. */
    private static Path engendrer(Path tmp) throws IOException {
        Path classes = projetCompile(tmp);
        Path dossier = tmp.resolve("diagrammes");

        UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputDirectory(dossier.toString())
            .build()
            .generate();

        return dossier;
    }

    private static String lire(Path fichier) throws IOException {
        assertTrue(Files.exists(fichier), "Fichier attendu et absent : " + fichier);
        return Files.readString(fichier, StandardCharsets.UTF_8);
    }

    @Test
    void leDossier_porteLIntegraleSaVueEtLaSourceDeChaqueImage(@TempDir Path tmp)
        throws IOException {
        Path dossier = engendrer(tmp);

        assertTrue(Files.exists(dossier.resolve("projet.puml")));
        assertTrue(Files.exists(dossier.resolve("projet.svg")));

        assertTrue(Files.exists(dossier.resolve("vues/scolarite.puml")));
        assertTrue(Files.exists(dossier.resolve("vues/scolarite.svg")));

        String ignorees = lire(dossier.resolve(UMLGenerator.FICHIER_DES_CLASSES_IGNOREES));
        assertTrue(ignorees.contains("Intendance"), ignorees);
    }

    @Test
    void laVueDEnsemble_renvoieVersLaVueQuiDetaille(@TempDir Path tmp) throws IOException {
        String projet = lire(engendrer(tmp).resolve("projet.puml"));

        assertTrue(projet.contains("[[vues/scolarite.svg]]"),
            "Les boîtes de la vue doivent renvoyer vers elle :\n" + projet);
    }

    @Test
    void leTitreDuFichierDeRegles_atteintLaSource(@TempDir Path tmp) throws IOException {
        assertTrue(lire(engendrer(tmp).resolve("projet.puml")).contains("title École"));
    }

    @Test
    void deuxAttributsReciproques_neDonnentQuUnTraitDansLeFichierLivre(@TempDir Path tmp)
        throws IOException {
        List<String> traits = PlantUmlAssert.lignesDeLien(lire(engendrer(tmp)
            .resolve("vues/scolarite.puml")));

        assertEquals(1, traits.size(),
            "Un seul trait attendu entre Eleve et Cours, obtenu : " + traits);
        assertTrue(traits.get(0).contains("\"inscrits 1..*\""), traits.get(0));
        assertTrue(traits.get(0).contains("\"cours 0..*\""), traits.get(0));
    }

    @Test
    void lHeritageEtLOperationAbstraite_sontLusDansLeCode(@TempDir Path tmp) throws IOException {
        String projet = lire(engendrer(tmp).resolve("projet.puml"));

        assertTrue(projet.contains("Personne <|-- Eleve"),
            "La généralisation doit être lue dans extends :\n" + projet);
        assertTrue(projet.contains("{abstract} valider()"),
            "UML écrit une opération sans corps en italique :\n" + projet);
        assertTrue(projet.contains("abstract class \"Personne\""),
            "Le mot-clé de la boîte doit dire ce que la classe est :\n" + projet);
    }

    @Test
    void lesSourcesLivrees_sontAccepteesParPlantUml(@TempDir Path tmp) throws IOException {
        Path dossier = engendrer(tmp);

        PlantUmlAssert.assertCompile(lire(dossier.resolve("projet.puml")));
        PlantUmlAssert.assertCompile(lire(dossier.resolve("vues/scolarite.puml")));
    }

    @Test
    void deuxGenerations_ecriventLeMemeFichier(@TempDir Path tmp) throws IOException {
        String premiere = lire(engendrer(tmp).resolve("projet.puml"));
        String seconde = lire(engendrer(tmp.resolve("bis")).resolve("projet.puml"));

        assertEquals(premiere, seconde);
    }

    @Test
    void aucuneSourceLivree_neContientDeMessageDErreur(@TempDir Path tmp) throws IOException {
        String projet = lire(engendrer(tmp).resolve("projet.puml"));

        assertFalse(projet.toLowerCase().contains("syntax error"), projet);
        assertTrue(projet.startsWith("@startuml"), projet);
        assertTrue(projet.stripTrailing().endsWith("@enduml"), projet);
    }
}
