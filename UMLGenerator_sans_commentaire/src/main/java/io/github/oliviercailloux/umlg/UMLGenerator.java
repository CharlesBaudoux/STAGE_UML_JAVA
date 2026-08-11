package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import io.github.oliviercailloux.umlg.extraction.ProjectScanner;
import io.github.oliviercailloux.umlg.extraction.ResultatDeScan;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.rendering.PlantUmlWriter;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
 * est un lien. Le fichier « umlg.json », posé à la racine du répertoire analysé, dit ce qui vaut
 * pour le projet entier : par exemple qu'un ImmutableList s'affiche « List ». Il est facultatif.
 */
public final class UMLGenerator {

    /**
     * Le nom du fichier qui, dans le dossier de sortie, nomme toutes les classes ignorées.
     *
     * <p>Le compte rendu, lui, s'arrête de les nommer au-delà d'une douzaine — c'est juste pour un
     * affichage, et c'est trop tôt pour le cas qui a motivé la fonctionnalité : un projet réel de
     * trente et une classes en avait quinze hors du diagramme, soit précisément trop pour être
     * nommées. Le fichier n'a pas cette limite ; il est là pour être ouvert quand le nombre a
     * donné l'alerte.
     */
    public static final String FICHIER_DES_CLASSES_IGNOREES = "ignored-classes.txt";

    private final Path sourceDirectory;
    private final String outputFileName;
    private final Path outputDirectory;
    private final String extension;
    private final UmlStyle style;
    private final ImmutableList<Path> classpath;
    private final boolean keepPrevious;

    private UMLGenerator(Builder builder) {
        this.sourceDirectory = builder.sourceDirectory;
        this.outputFileName = builder.outputFileName;
        this.outputDirectory = builder.outputDirectory;
        this.extension = builder.extension;
        this.style = builder.style;
        this.classpath = builder.classpath;
        this.keepPrevious = builder.keepPrevious;
    }

    /**
     * Ouvre la configuration d'une génération.
     *
     * @return un builder neuf, où seul le répertoire source reste à fournir.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Les noms de couleurs que {@code @UMLClass(color = ...)} accepte, en plus des codes
     * hexadécimaux.
     *
     * <p>Le vocabulaire est celui de PlantUML, et il était jusqu'ici enfermé dans un paquetage non
     * exporté : l'utilisateur dont la couleur était refusée apprenait qu'elle n'existe pas, sans
     * pouvoir demander lesquelles existent. La liste passe par la façade parce que c'est le seul
     * endroit exporté d'où la poser — et qu'une question sur ce que la bibliothèque accepte est
     * une question à la bibliothèque, pas à un de ses rouages.
     *
     * @return les noms acceptés, par ordre alphabétique.
     */
    public static ImmutableSortedSet<String> couleursConnues() {
        return ColorCodec.nomsConnus();
    }

    /**
     * Lit le code annoté et écrit le ou les diagrammes demandés.
     *
     * <p><b>Pourquoi la bibliothèque n'écrit plus sur la sortie standard.</b> Elle y écrivait, et
     * le plugin Maven relayait les mêmes lignes dans son log : l'utilisateur d'un build lisait
     * tout en double, et la dernière ligne — celle qui dit où trouver la liste des ignorées — se
     * perdait au passage, faute d'appartenir au compte rendu.
     *
     * <p>Choisir le canal n'appartient pas à une bibliothèque. Elle rend ce qu'elle a à dire ; la
     * ligne de commande l'écrit sur la sortie standard, le plugin Maven le passe à son log, et un
     * appelant programmatique en fait ce qu'il veut — échouer sur « 15 classes ignorées », par
     * exemple.
     *
     * @return le compte rendu : ce qui a été lu, ce qui a été ignoré, et les fichiers écrits.
     */
    public RapportDeGeneration generate() {
        try {
            ResultatDeScan resultat = ProjectScanner.scan(sourceDirectory, classpath);
            UmlProject project = resultat.projet();

            ImmutableList<Path> ecrits;
            if (outputDirectory != null) {
                ecrits = PlantUmlWriter.writeViews(project, outputDirectory, extension, style,
                    keepPrevious);
            } else {
                PlantUmlWriter.write(project, outputFileName, style);
                ecrits = ImmutableList.of(Paths.get(outputFileName));
            }

            ImmutableList<String> ignorees = resultat.classesIgnorees();
            Path liste = ecrireListeDesIgnorees(ignorees, ecrits);

            return new RapportDeGeneration(project.entities().size(), ignorees, ecrits, liste);
        } catch (FileSystemException e) {
            throw new RuntimeException("Failed to generate the UML diagram: the output path "
                + e.getFile() + " cannot be written ("
                + (e.getReason() == null ? e.getClass().getSimpleName() : e.getReason())
                + "). Check the directory exists and that you have permission to write in it.", e);
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException(
                "Failed to generate the UML diagram (source directory: " + sourceDirectory
                    + ", output: " + (outputDirectory != null ? outputDirectory : outputFileName)
                    + "): " + e.getMessage(), e);
        }
    }

    private static Path ecrireListeDesIgnorees(ImmutableList<String> ignorees,
        ImmutableList<Path> ecrits) throws IOException {
        Path fichier = dossierDeSortie(ecrits.get(0)).resolve(FICHIER_DES_CLASSES_IGNOREES);
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

    static Path dossierDeSortie(Path ecrit) {
        Path parent = ecrit.getParent();
        return parent == null ? Paths.get("") : parent;
    }

    /**
     * Utilisation en ligne de commande.
     *
     * <p>Le second argument dit à lui seul ce qu'on veut : un nom finissant par {@code .svg} ou
     * {@code .png} demande ce seul fichier, tout autre nom demande un dossier — la vue intégrale à
     * sa racine, les autres dans un sous-dossier. Deviner ainsi évite une option de plus, et se
     * lit sans documentation : personne n'appelle « diagrammes » un fichier, ni
     * « architecture.svg » un répertoire.
     *
     * @param args le répertoire des classes compilées, puis facultativement le fichier ou le
     *     dossier de sortie, puis le format des images d'un dossier ({@code png} ou {@code svg}).
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: UMLGenerator <source-directory> "
                + "[output-file.svg|output-directory] [png|svg]");
            System.exit(1);
            return;
        }

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
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            System.err.println("Error: " + (cause.getMessage() == null
                ? e.getMessage() : cause.getMessage()));
            System.exit(1);
        }
    }

    /**
     * De quoi configurer une génération, un appel à la fois.
     *
     * <p>Le builder valide au fur et à mesure : une erreur de chemin ou d'extension se signale dès
     * l'appel fautif, et non au bout de plusieurs secondes d'analyse.
     */
    public static final class Builder {
        private Path sourceDirectory;
        private String outputFileName = "diagramme_architecture.svg";
        private Path outputDirectory;
        private String extension = ".svg";
        private UmlStyle style = UmlStyle.PLANTUML;
        private ImmutableList<Path> classpath = ImmutableList.of();
        private boolean keepPrevious;

        private Builder() {}

        /**
         * Le répertoire des {@code .class} compilés, et non des sources {@code .java}.
         *
         * @param path le chemin de ce répertoire. Il doit exister.
         * @return ce builder.
         */
        public Builder sourceDirectory(String path) {
            checkNotNull(path, "The source path cannot be null.");
            Path p = Paths.get(path);
            checkArgument(Files.exists(p) && Files.isDirectory(p),
                "The given source directory does not exist or is not a directory: %s", path);

            this.sourceDirectory = p;
            return this;
        }

        /**
         * Demande un seul fichier image, plutôt qu'un dossier.
         *
         * @param fileName son nom, dont l'extension décide du format : {@code .svg} ou
         *     {@code .png}.
         * @return ce builder.
         */
        public Builder outputFile(String fileName) {
            checkNotNull(fileName, "The file name cannot be null.");
            checkArgument(PlantUmlWriter.supporte(fileName),
                "The output format must be .png or .svg (got: %s)", fileName);

            this.outputFileName = fileName;
            return this;
        }

        /**
         * Le dossier où déposer tous les diagrammes du projet : la vue intégrale à sa racine, les
         * autres dans un sous-dossier « vues ».
         *
         * <p>C'est la forme à préférer dès qu'un projet déclare des vues, et la seule qui les
         * écrive toutes. Le dossier est créé s'il n'existe pas ; ce qu'il advient de la génération
         * précédente se règle par {@link #keepPrevious(boolean)}.
         *
         * @param path le chemin de ce dossier.
         * @return ce builder.
         */
        public Builder outputDirectory(String path) {
            checkNotNull(path, "The output directory cannot be null.");
            this.outputDirectory = Paths.get(path);
            return this;
        }

        /**
         * Conserver la génération précédente au lieu de l'effacer.
         *
         * <p>Sous {@code keepPrevious}, rien n'est écrasé : cette génération-ci est écrite dans un
         * dossier voisin numéroté — « diagrammes-2 », « diagrammes-3 » —, ce qui permet de
         * comparer avant et après une modification du code. Sans elle, le dossier demandé est vidé
         * des seuls fichiers que nous y avons écrits, et décrit donc toujours l'état courant.
         *
         * <p><b>Pourquoi ce réglage n'est pas dans umlg.json.</b> Il y a été, sous
         * {@code output replace} et {@code output keep}, et il en est sorti au test qui décide de
         * tout ce qu'on y écrit : la ligne dit-elle quelque chose du modèle ? Le sort des fichiers
         * de la fois précédente n'en dit rien — mêmes boîtes, mêmes traits, mêmes membres. C'est
         * une option d'appel, au même titre qu'{@link UmlStyle}, et elle varie d'ailleurs d'un
         * appel à l'autre là où le fichier de règles, lui, est versionné avec le code.
         *
         * @param keepPrevious vrai pour écrire à côté plutôt que d'effacer.
         * @return ce builder.
         */
        public Builder keepPrevious(boolean keepPrevious) {
            this.keepPrevious = keepPrevious;
            return this;
        }

        /**
         * Le format des images écrites dans le dossier : {@code .svg} (défaut) ou {@code .png}.
         *
         * <p>Séparé du nom de fichier parce qu'il n'y en a plus un seul : vingt vues partagent un
         * format, et le répéter vingt fois n'aurait aucun sens.
         *
         * @param extensionDemandee le format voulu, avec ou sans le point.
         * @return ce builder.
         */
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

        /**
         * L'habillage du diagramme : celui de PlantUML, ou celui de la bibliothèque.
         *
         * <p>Ce choix ne change jamais ce que le diagramme dit, seulement son apparence.
         *
         * @param style l'habillage voulu.
         * @return ce builder.
         */
        public Builder style(UmlStyle style) {
            this.style = checkNotNull(style, "The style cannot be null. Use UmlStyle.PLANTUML.");
            return this;
        }

        /**
         * Les dépendances du projet analysé : jars et répertoires de classes, en plus du
         * répertoire source lui-même.
         *
         * <p>Nécessaire dès que les classes analysées mentionnent autre chose que le JDK et les
         * annotations de la bibliothèque : la réflexion doit pouvoir charger le parent d'une
         * classe, le type d'un attribut — et ils vivent dans les dépendances du projet, pas dans
         * les nôtres. C'est le plugin Maven qui s'en sert : il connaît le classpath d'exécution du
         * projet et le passe ici tel quel.
         *
         * @param entrees les chemins de ces dépendances.
         * @return ce builder.
         */
        public Builder classpath(List<String> entrees) {
            checkNotNull(entrees, "The classpath cannot be null (use an empty list).");
            this.classpath = entrees.stream()
                .map(Paths::get)
                .collect(ImmutableList.toImmutableList());
            return this;
        }

        /**
         * Fige la configuration.
         *
         * @return le générateur, prêt à {@link UMLGenerator#generate()}.
         * @throws NullPointerException si le répertoire source n'a pas été fourni : il n'a pas de
         *     défaut raisonnable.
         */
        public UMLGenerator build() {
            checkNotNull(sourceDirectory,
                "The source directory is mandatory. Use .sourceDirectory(...)");
            return new UMLGenerator(this);
        }
    }
}
