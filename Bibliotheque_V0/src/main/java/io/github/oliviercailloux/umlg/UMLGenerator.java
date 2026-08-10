package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.ProjectScanner;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.rendering.PlantUmlWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * La façade : le seul point d'entrée de la bibliothèque.
 *
 * <p>Usage typique :
 * <pre>{@code
 * UMLGenerator.builder()
 *     .sourceDirectory("target/classes")
 *     .outputFile("architecture.svg")
 *     .build()
 *     .generate();
 * }</pre>
 *
 * <p>Elle ne fait qu'enchaîner les deux moitiés du travail : ProjectScanner lit le code annoté et
 * en tire un modèle, PlantUmlWriter transforme ce modèle en image.
 *
 * <p>Deux choses gouvernent le diagramme, et elles ne se déclarent pas au même endroit. Les
 * annotations disent ce qui vaut pour un élément — cette classe-ci est une boîte, cet attribut-là
 * est un lien. Le fichier « umlg.rules », posé à la racine du répertoire analysé, dit ce qui vaut
 * pour le projet entier : par exemple qu'un ImmutableList s'affiche « List ». Il est facultatif.
 */
public final class UMLGenerator {

    private final Path sourceDirectory;
    private final String outputFileName;
    private final Path outputDirectory;
    private final String extension;
    private final UmlStyle style;
    private final ImmutableList<Path> classpath;
    private final boolean keepPrevious;

    // Privé : on ne passe que par le builder, seul à valider les paramètres.
    private UMLGenerator(Builder builder) {
        this.sourceDirectory = builder.sourceDirectory;
        this.outputFileName = builder.outputFileName;
        this.outputDirectory = builder.outputDirectory;
        this.extension = builder.extension;
        this.style = builder.style;
        this.classpath = builder.classpath;
        this.keepPrevious = builder.keepPrevious;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Les noms de couleurs que @UMLClass(color = ...) accepte, en plus des codes hexadécimaux.
    //
    // Le vocabulaire est celui de PlantUML, et il était jusqu'ici enfermé dans un paquetage non
    // exporté : l'utilisateur dont la couleur était refusée apprenait qu'elle n'existe pas, sans
    // pouvoir demander lesquelles existent. La liste passe par la façade parce que c'est le seul
    // endroit exporté d'où la poser — et qu'une question sur ce que la bibliothèque accepte est
    // une question à la bibliothèque, pas à un de ses rouages.
    public static com.google.common.collect.ImmutableSortedSet<String> couleursConnues() {
        return io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec.nomsConnus();
    }

    // Le nom du fichier qui, dans le dossier de sortie, nomme toutes les classes ignorées.
    //
    // Le compte rendu, lui, s'arrête de les nommer au-delà d'une douzaine — c'est juste pour un
    // affichage, et c'est trop tôt pour le cas qui a motivé la fonctionnalité : un projet réel
    // de trente et une classes en avait quinze hors du diagramme, soit précisément trop pour
    // être nommées. Le fichier n'a pas cette limite ; il est là pour être ouvert quand le nombre
    // a donné l'alerte.
    public static final String FICHIER_DES_CLASSES_IGNOREES = "ignored-classes.txt";

    // Rend le compte rendu, et ne l'affiche pas.
    //
    // POURQUOI LA BIBLIOTHÈQUE N'ÉCRIT PLUS SUR LA SORTIE STANDARD
    //
    // Elle y écrivait, et le plugin Maven relayait les mêmes lignes dans son log : l'utilisateur
    // d'un build lisait tout en double, et la dernière ligne — celle qui dit où trouver la liste
    // des ignorées — se perdait au passage, faute d'appartenir au compte rendu.
    //
    // Choisir le canal n'appartient pas à une bibliothèque. Elle rend ce qu'elle a à dire ; la
    // ligne de commande l'écrit sur la sortie standard, le plugin Maven le passe à son log, et un
    // appelant programmatique en fait ce qu'il veut — échouer sur « 15 classes ignorées », par
    // exemple.
    public RapportDeGeneration generate() {
        try {
            // Étape 1 : le code annoté devient un modèle, vues comprises.
            UmlProject project = ProjectScanner.scan(sourceDirectory, classpath);

            // Étape 2 : le modèle devient une ou plusieurs images sur le disque.
            //
            // Un dossier a été demandé : toutes les vues sont écrites, l'intégrale à la racine et
            // les autres dans un sous-dossier. Sinon, on écrit le seul diagramme d'ensemble dans
            // le fichier nommé — la forme d'origine, que rien n'oblige à abandonner.
            ImmutableList<Path> ecrits;
            if (outputDirectory != null) {
                ecrits = PlantUmlWriter.writeViews(project, outputDirectory, extension, style,
                    keepPrevious);
            } else {
                PlantUmlWriter.write(project, outputFileName, style);
                ecrits = ImmutableList.of(Paths.get(outputFileName));
            }

            // La liste complète est écrite dans les deux modes. Elle ne l'était que pour un
            // dossier, si bien qu'un utilisateur du mode fichier unique lisait « 37 ignored »
            // sans aucun moyen de savoir lesquelles.
            ImmutableList<String> ignorees = ProjectScanner.classesIgnorees();
            Path liste = ecrireListeDesIgnorees(ignorees, ecrits);

            return new RapportDeGeneration(project.entities().size(), ignorees, ecrits, liste);
        } catch (java.nio.file.FileSystemException e) {
            // Le message d'une FileSystemException se réduit au chemin — « /tmp/ro/x » —, et
            // l'enveloppe générique le répétait donc sans rien apprendre à personne. La raison
            // vit dans le type de l'exception et dans son « reason », pas dans son message.
            throw new RuntimeException("Failed to generate the UML diagram: the output path "
                + e.getFile() + " cannot be written ("
                + (e.getReason() == null ? e.getClass().getSimpleName() : e.getReason())
                + "). Check the directory exists and that you have permission to write in it.", e);
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

    // Écrit la liste complète des classes ignorées à la racine du dossier de sortie, ou rend
    // null s'il n'y a rien à nommer.
    //
    // Le fichier périmé d'une génération précédente est retiré d'abord : une liste qui ne
    // correspond plus au dessin d'à côté mentirait d'autant mieux qu'elle a l'air à sa place.
    private static Path ecrireListeDesIgnorees(ImmutableList<String> ignorees,
        ImmutableList<Path> ecrits) throws java.io.IOException {
        Path racine = ecrits.get(0).getParent();
        if (racine == null) {
            return null;
        }
        Path fichier = racine.resolve(FICHIER_DES_CLASSES_IGNOREES);
        Files.deleteIfExists(fichier);

        if (ignorees.isEmpty()) {
            return null;
        }
        StringBuilder contenu = new StringBuilder();
        contenu.append("Classes seen without @UMLClass, hence not drawn (")
               .append(ignorees.size()).append("):\n");
        for (String classe : ignorees) {
            contenu.append(classe).append('\n');
        }
        Files.writeString(fichier, contenu.toString());
        return fichier;
    }

    // Utilisation en ligne de commande.
    //
    // Le second argument dit à lui seul ce qu'on veut : un nom finissant par « .svg » ou « .png »
    // demande ce seul fichier, tout autre nom demande un dossier — la vue intégrale à sa racine,
    // les autres dans un sous-dossier. Deviner ainsi évite une option de plus, et se lit sans
    // documentation : personne n'appelle « diagrammes » un fichier, ni « architecture.svg » un
    // répertoire.
    //
    // Le format des images d'un dossier se règle par un troisième argument, « png » ou « svg ».
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: UMLGenerator <source-directory> "
                + "[output-file.svg|output-directory] [png|svg]");
            System.exit(1);
            // Inutile après exit(), mais rend explicite qu'on n'ira pas plus loin — et permet
            // aux tests de remplacer exit() sans que la suite s'exécute quand même.
            return;
        }

        // Tout ce qui suit peut échouer sur une faute de l'utilisateur : un répertoire qui
        // n'existe pas, un fichier de règles fautif, une couleur inconnue. Ces messages-là sont
        // écrits pour être lus, et une trace d'exécution de douze lignes les noyait — alors même
        // que la ligne utile s'y trouvait, en tête. On dit le message, et la trace n'est
        // conservée que pour ce qu'elle explique vraiment : un défaut de la bibliothèque.
        try {
            Builder builder = builder().sourceDirectory(args[0]);

            if (args.length >= 2) {
                if (PlantUmlWriter.supporte(args[1])) {
                    builder.outputFile(args[1]);
                } else {
                    builder.outputDirectory(args[1]);
                }
            }
            if (args.length >= 3) {
                builder.outputFormat(args[2]);
            }
            System.out.println(builder.build().generate().resume());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Une faute de l'appelant, ou un environnement incomplet : le message dit quoi faire.
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            // L'enveloppe de generate(), dont la cause porte le vrai message.
            Throwable cause = e.getCause() == null ? e : e.getCause();
            System.err.println("Error: " + (cause.getMessage() == null
                ? e.getMessage() : cause.getMessage()));
            System.exit(1);
        }
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
        // Vide par défaut : le répertoire analysé se suffit quand ses classes ne mentionnent
        // que le JDK et les annotations de la bibliothèque.
        private ImmutableList<Path> classpath = ImmutableList.of();
        // Faux par défaut : le dossier décrit l'état courant du code, et une vue retirée du
        // fichier de règles ne laisse pas derrière elle une image que plus rien ne met à jour.
        private boolean keepPrevious;

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
        // précédente se règle par keepPrevious.
        public Builder outputDirectory(String path) {
            checkNotNull(path, "The output directory cannot be null.");
            this.outputDirectory = Paths.get(path);
            return this;
        }

        // Conserver la génération précédente au lieu de l'effacer.
        //
        // Sous keepPrevious, rien n'est écrasé : cette génération-ci est écrite dans un dossier
        // voisin numéroté — « diagrammes-2 », « diagrammes-3 » —, ce qui permet de comparer avant
        // et après une modification du code. Sans elle, le dossier demandé est vidé des seuls
        // fichiers que nous y avons écrits, et décrit donc toujours l'état courant.
        //
        // POURQUOI CE RÉGLAGE N'EST PAS DANS umlg.rules
        //
        // Il y a été, sous « output replace » et « output keep », et il en est sorti au test qui
        // décide de tout ce qu'on y écrit : la ligne dit-elle quelque chose du modèle ? Le sort
        // des fichiers de la fois précédente n'en dit rien — mêmes boîtes, mêmes traits, mêmes
        // membres. C'est une option d'appel, au même titre qu'UmlStyle, et elle varie d'ailleurs
        // d'un appel à l'autre là où le fichier de règles, lui, est versionné avec le code.
        public Builder keepPrevious(boolean keepPrevious) {
            this.keepPrevious = keepPrevious;
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

        // Les dépendances du projet analysé : jars et répertoires de classes, en plus du
        // répertoire source lui-même.
        //
        // Nécessaire dès que les classes analysées mentionnent autre chose que le JDK et les
        // annotations de la bibliothèque : la réflexion doit pouvoir charger le parent d'une
        // classe, le type d'un attribut — et ils vivent dans les dépendances du projet, pas dans
        // les nôtres. C'est le plugin Maven qui s'en sert : il connaît le classpath d'exécution
        // du projet et le passe ici tel quel.
        public Builder classpath(java.util.List<String> entrees) {
            checkNotNull(entrees, "The classpath cannot be null (use an empty list).");
            this.classpath = entrees.stream()
                .map(Paths::get)
                .collect(ImmutableList.toImmutableList());
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
