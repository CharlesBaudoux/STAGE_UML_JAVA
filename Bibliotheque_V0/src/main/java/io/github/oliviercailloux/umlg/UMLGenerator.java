package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.ProjectScanner;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
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
    private final Path outputDirectory;
    private final String extension;
    private final UmlStyle style;

    // Privé : on ne passe que par le builder, seul à valider les paramètres.
    private UMLGenerator(Builder builder) {
        this.sourceDirectory = builder.sourceDirectory;
        this.outputFileName = builder.outputFileName;
        this.outputDirectory = builder.outputDirectory;
        this.extension = builder.extension;
        this.style = builder.style;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void generate() {
        try {
            // Étape 1 : le code annoté devient un modèle, vues comprises.
            UmlProject project = ProjectScanner.scan(sourceDirectory);

            // Étape 2 : le modèle devient une ou plusieurs images sur le disque.
            //
            // Un dossier a été demandé : toutes les vues sont écrites, l'intégrale à la racine et
            // les autres dans un sous-dossier. Sinon, on écrit le seul diagramme d'ensemble dans
            // le fichier nommé — la forme d'origine, que rien n'oblige à abandonner.
            if (outputDirectory != null) {
                ImmutableList<Path> ecrits = PlantUmlWriter.writeViews(project,
                    outputDirectory, extension, style, Regles.duProjet(sourceDirectory).sortie());

                System.out.println("Success: " + ecrits.size() + " diagram(s) generated -> "
                    + ecrits.get(0).getParent());
                return;
            }
            PlantUmlWriter.write(project, outputFileName, style);
        } catch (Exception e) {
            // Les deux étapes déclarent « throws Exception » (entrées-sorties, chargement de
            // classes). On les enveloppe en exception non vérifiée pour ne pas l'imposer à
            // l'appelant, en rappelant les deux paramètres — presque toutes les pannes viennent
            // d'un répertoire ou d'un nom de fichier inattendu. La cause d'origine est conservée.
            throw new RuntimeException(
                "Failed to generate the UML diagram (source directory: " + sourceDirectory
                    + ", output: " + (outputDirectory != null ? outputDirectory : outputFileName)
                    + "): " + e.getMessage(), e);
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
        // Null tant qu'aucun dossier n'est demandé : c'est ce qui distingue les deux sorties.
        private Path outputDirectory;
        private String extension = ".svg";
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

        // Le dossier où déposer tous les diagrammes du projet : la vue intégrale à sa racine,
        // les autres dans un sous-dossier « vues ».
        //
        // C'est la forme à préférer dès qu'un projet déclare des vues, et la seule qui les écrive
        // toutes. Le dossier est créé s'il n'existe pas ; ce qu'il advient de la génération
        // précédente se règle dans umlg.rules, par « output replace » ou « output keep ».
        public Builder outputDirectory(String path) {
            checkNotNull(path, "The output directory cannot be null.");
            this.outputDirectory = Paths.get(path);
            return this;
        }

        // Le format des images écrites dans le dossier : « .svg » (défaut) ou « .png ».
        //
        // Séparé du nom de fichier parce qu'il n'y en a plus un seul : vingt vues partagent un
        // format, et le répéter vingt fois n'aurait aucun sens.
        public Builder outputFormat(String extensionDemandee) {
            checkNotNull(extensionDemandee, "The output format cannot be null.");
            String normalisee = extensionDemandee.startsWith(".")
                ? extensionDemandee
                : "." + extensionDemandee;
            checkArgument(PlantUmlWriter.supporte("x" + normalisee),
                "The output format must be .png or .svg (got: %s)", extensionDemandee);

            this.extension = normalisee;
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
