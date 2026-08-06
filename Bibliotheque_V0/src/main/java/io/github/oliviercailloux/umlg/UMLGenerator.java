package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.extraction.ProjectScanner;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.rendering.PlantUmlWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// La façade : le seul point d'entrée de la bibliothèque.
//
// Usage typique :
//   UMLGenerator.builder()
//       .sourceDirectory("target/classes")
//       .outputFile("architecture.svg")
//       .build()
//       .generate();
//
// Elle ne fait qu'enchaîner les deux moitiés du travail : ProjectScanner lit le code annoté et
// en tire un modèle, PlantUmlWriter transforme ce modèle en image.
//
// Deux choses gouvernent le diagramme, et elles ne se déclarent pas au même endroit. Les
// annotations disent ce qui vaut pour un élément — cette classe-ci est une boîte, cet attribut-là
// est un lien. Le fichier « umlg.rules », posé à la racine du répertoire analysé, dit ce qui vaut
// pour le projet entier : par exemple qu'un ImmutableList s'affiche « List ». Il est facultatif ;
// voir Regles pour sa forme.
public final class UMLGenerator {

    private final Path sourceDirectory;
    private final String outputFileName;
    private final UmlStyle style;

    // Privé : on ne passe que par le builder, seul à valider les paramètres.
    private UMLGenerator(Builder builder) {
        this.sourceDirectory = builder.sourceDirectory;
        this.outputFileName = builder.outputFileName;
        this.style = builder.style;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void generate() {
        try {
            // Étape 1 : le code annoté devient un modèle.
            UmlProject project = ProjectScanner.scan(sourceDirectory);
            // Étape 2 : le modèle devient une image sur le disque.
            PlantUmlWriter.write(project, outputFileName, style);
        } catch (Exception e) {
            // Les deux étapes déclarent « throws Exception » (entrées-sorties, chargement de
            // classes). On les enveloppe en exception non vérifiée pour ne pas l'imposer à
            // l'appelant, en rappelant les deux paramètres — presque toutes les pannes viennent
            // d'un répertoire ou d'un nom de fichier inattendu. La cause d'origine est conservée.
            throw new RuntimeException(
                "Failed to generate the UML diagram (source directory: " + sourceDirectory
                    + ", output file: " + outputFileName + "): " + e.getMessage(), e);
        }
    }

    // Utilisation en ligne de commande, l'équivalent des trois appels du builder.
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: UMLGenerator <source-directory> [output-file]");
            System.exit(1);
            // Inutile après exit(), mais rend explicite qu'on n'ira pas plus loin — et permet
            // aux tests de remplacer exit() sans que la suite s'exécute quand même.
            return;
        }

        Builder builder = builder().sourceDirectory(args[0]);
        // Le fichier de sortie est facultatif : sans lui, le défaut du builder s'applique.
        if (args.length >= 2) {
            builder.outputFile(args[1]);
        }
        builder.build().generate();
    }

    // Le builder valide au fur et à mesure : une erreur de chemin ou d'extension se signale dès
    // l'appel fautif, et non au bout de plusieurs secondes d'analyse.
    public static final class Builder {
        private Path sourceDirectory;
        private String outputFileName = "diagramme_architecture.svg"; // Smart Default
        // L'apparence de PlantUML : ne rien imposer est le défaut le moins présomptueux.
        private UmlStyle style = UmlStyle.PLANTUML;

        private Builder() {}

        // Le répertoire des .class compilés, pas des sources .java.
        public Builder sourceDirectory(String path) {
            checkNotNull(path, "The source path cannot be null.");
            Path p = Paths.get(path);
            // Vérifié ici plutôt qu'au scan : le message peut alors désigner l'appel fautif.
            checkArgument(Files.exists(p) && Files.isDirectory(p),
                "The given source directory does not exist or is not a directory: %s", path);

            this.sourceDirectory = p;
            return this;
        }

        public Builder outputFile(String fileName) {
            checkNotNull(fileName, "The file name cannot be null.");
            // C'est l'extension qui décide du format ; le writer est seul juge de ce qu'il sait
            // produire, d'où la délégation.
            checkArgument(PlantUmlWriter.supporte(fileName),
                "The output format must be .png or .svg (got: %s)", fileName);

            this.outputFileName = fileName;
            return this;
        }

        // L'habillage du diagramme : celui de PlantUML, ou celui de la bibliothèque. Ce choix ne
        // change jamais ce que le diagramme dit, seulement son apparence — voir UmlStyle.
        public Builder style(UmlStyle style) {
            this.style = checkNotNull(style, "The style cannot be null. Use UmlStyle.PLANTUML.");
            return this;
        }

        // Dernière vérification : le répertoire source n'a pas de défaut raisonnable, il doit
        // avoir été fourni.
        public UMLGenerator build() {
            checkNotNull(sourceDirectory,
                "The source directory is mandatory. Use .sourceDirectory(...)");
            return new UMLGenerator(this);
        }
    }
}
