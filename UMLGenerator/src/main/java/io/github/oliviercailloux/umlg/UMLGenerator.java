package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

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
import java.util.Optional;

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
     * <p><b>Les avertissements sont dans le compte rendu.</b> Il n'y a rien à poser ni à retirer
     * pour les lire : {@code rapport.avertissements()} les porte tous, et
     * {@code if (rapport.avertissements().isEmpty())} s'écrit donc. {@link Avertissements} reste
     * là pour l'autre besoin, celui d'un relais qui les voit arriver au lieu de les relire.
     *
     * @return le compte rendu : ce qui a été lu, ce qui a été ignoré, les fichiers écrits, et ce
     *     qu'il y a eu à signaler en chemin.
     */
    public RapportDeGeneration generate() {
        // Les avertissements de cette génération-ci entrent dans le compte rendu qu'elle rend.
        // C'est generate() qui pose le canal et le retire, et non plus l'appelant : lui demander
        // d'encadrer son appel d'un versLeCanal et de sa fermeture, c'était lui demander de ne pas
        // oublier la seconde — un canal laissé en place capte la génération d'après, sur un fil
        // qu'un pool réutilise.
        //
        // « en plus » et non « à la place » : le canal que l'appelant avait posé — le log de
        // Maven — continue de tout recevoir au fil de l'eau. Le remplacer ferait taire le relais
        // temps réel exactement pendant le moment où il sert.
        ImmutableList.Builder<String> avertissements = ImmutableList.builder();
        try (Avertissements.Canal capture =
            Avertissements.enPlusDuCanalCourant(avertissements::add)) {
            return genere(avertissements);
        }
    }

    // Le corps de generate(), une fois le canal des avertissements en place.
    private RapportDeGeneration genere(ImmutableList.Builder<String> avertissements) {
        try {
            // Étape 1 : le code annoté devient un modèle, vues comprises. Le scan rend du même
            // coup ce qu'il a laissé de côté : ces classes ne sont pas dans le modèle, et c'est
            // précisément ce qu'il y a à en dire.
            ResultatDeScan resultat = ProjectScanner.scan(sourceDirectory, classpath);
            UmlProject project = resultat.projet();

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
                signalerLesVuesPerdues(project);
                PlantUmlWriter.write(project, outputFileName, style);
                ecrits = ImmutableList.of(Paths.get(outputFileName));
            }

            // La liste complète est écrite dans les deux modes. Elle ne l'était que pour un
            // dossier, si bien qu'un utilisateur du mode fichier unique lisait « 37 ignored »
            // sans aucun moyen de savoir lesquelles.
            ImmutableList<String> ignorees = resultat.classesIgnorees();
            Optional<Path> liste = ecrireListeDesIgnorees(ignorees, ecrits);

            return new RapportDeGeneration(project.entities().size(), ignorees, ecrits, liste,
                avertissements.build());
        } catch (FileSystemException e) {
            // Le message d'une FileSystemException se réduit au chemin — « /tmp/ro/x » —, et
            // l'enveloppe générique le répétait donc sans rien apprendre à personne. La raison
            // vit dans le type de l'exception et dans son « reason », pas dans son message.
            throw new RuntimeException("Failed to generate the UML diagram: the output path "
                + e.getFile() + " cannot be written ("
                + (e.getReason() == null ? e.getClass().getSimpleName() : e.getReason())
                + "). Check the directory exists and that you have permission to write in it.", e);
        } catch (IOException | RuntimeException e) {
            // Les deux étapes déclarent « throws IOException » : lire un répertoire de classes
            // et écrire une image sont des entrées-sorties, et rien d'autre. On les enveloppe en
            // exception non vérifiée pour ne pas l'imposer à l'appelant, en rappelant les deux
            // paramètres — presque toutes les pannes viennent d'un répertoire ou d'un nom de
            // fichier inattendu. La cause d'origine est conservée.
            //
            // Les RuntimeException sont enrobées elles aussi, et c'est voulu : un scan qui
            // refuse un projet sans @UMLClass nomme le répertoire fautif, mais pas le fichier
            // qu'on lui demandait d'écrire. L'enrobage ajoute le second, et c'est ce qui rend le
            // message diagnostiquable. Le catch portait sur Exception, ce qui revenait au même
            // tant que les deux étapes déclaraient Exception ; maintenant qu'elles ne le font
            // plus, l'écrire ainsi dit ce qui est réellement attrapé, et laisse passer les Error.
            throw new RuntimeException(
                "Failed to generate the UML diagram (source directory: " + sourceDirectory
                    + ", output: " + (outputDirectory != null ? outputDirectory : outputFileName)
                    + "): " + e.getMessage(), e);
        }
    }

    // Ce qu'un fichier unique ne peut pas contenir.
    //
    // Le projet déclare des vues — dans son fichier de règles, ou par « views by package » — et la
    // sortie demandée est un fichier : seul le diagramme d'ensemble sera écrit, et les autres
    // seront perdues sans un mot. C'est le troisième des réglages qui se contredisaient en
    // silence, et le seul qui ne se voie pas à la lecture du code d'appel : rien, dans
    // « .outputFile("archi.svg") », ne dit ce que le fichier de règles a demandé par ailleurs.
    //
    // Le test porte sur « plus d'une » et non sur « au moins une » : la première vue est
    // l'intégrale, que ConstructeurDeVues place toujours en tête et que le fichier unique contient
    // précisément. Ce sont les suivantes qui n'ont nulle part où aller.
    private static void signalerLesVuesPerdues(UmlProject project) {
        int perdues = project.views().size() - 1;
        if (perdues > 0) {
            Avertissements.dire("this project declares " + perdues + " partial view(s), which a "
                + "single output file cannot hold: only the whole-project diagram is written. "
                + "Ask for a directory — .outputDirectory(\"diagrammes\") — to get them all.");
        }
    }

    // Écrit la liste complète des classes ignorées à la racine du dossier de sortie, ou rend un
    // Optional vide s'il n'y a rien à nommer.
    //
    // Le fichier périmé d'une génération précédente est retiré d'abord : une liste qui ne
    // correspond plus au dessin d'à côté mentirait d'autant mieux qu'elle a l'air à sa place.
    private static Optional<Path> ecrireListeDesIgnorees(ImmutableList<String> ignorees,
        ImmutableList<Path> ecrits) throws IOException {
        Path fichier = dossierDeSortie(ecrits.get(0)).resolve(FICHIER_DES_CLASSES_IGNOREES);
        Files.deleteIfExists(fichier);

        if (ignorees.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder contenu = new StringBuilder();
        contenu.append("Classes seen without @UMLClass, hence not drawn (")
               .append(ignorees.size()).append("):\n");
        for (String classe : ignorees) {
            contenu.append(classe).append('\n');
        }
        Files.writeString(fichier, contenu.toString());
        return Optional.of(fichier);
    }

    // Le dossier où atterrit un fichier écrit.
    //
    // getParent() rend null pour un chemin d'un seul segment — « diagramme.svg » —, et ce null ne
    // dit pas « nulle part » mais « le répertoire courant ». Le prendre pour une absence de
    // destination faisait renoncer à écrire la liste, et c'est précisément la forme qu'ont les
    // deux invocations les plus courantes : le nom par défaut, et un nom donné sans dossier. Le
    // mode dossier, lui, ne passait jamais par là — d'où un défaut que seul le mode fichier
    // unique montrait, et que « ./diagramme.svg » suffisait à masquer.
    //
    // Visible du paquetage plutôt que privée : le cas fautif est un chemin relatif d'un seul
    // segment, que rien ne permet de produire depuis un @TempDir — dont les chemins sont tous
    // absolus, donc tous pourvus d'un parent. Le contrôle se fait donc ici, sur la règle.
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

    /**
     * De quoi configurer une génération, un appel à la fois.
     *
     * <p>Le builder valide au fur et à mesure : une erreur de chemin ou d'extension se signale dès
     * l'appel fautif, et non au bout de plusieurs secondes d'analyse. Ce qu'un appel ne peut pas
     * juger seul — la cohérence de deux réglages entre eux — est vérifié par {@link #build()},
     * dernier moment où la configuration est encore complète et rien n'a été calculé.
     */
    public static final class Builder {
        private Path sourceDirectory;
        private String outputFileName = "diagramme_architecture.svg"; // Smart Default
        // Null tant qu'aucun dossier n'est demandé : c'est ce qui distingue les deux sorties.
        private Path outputDirectory;
        private String extension = ".svg";
        // Ce que l'utilisateur a demandé lui-même, par opposition aux défauts ci-dessus. Deux
        // défauts ne se contredisent pas ; deux demandes, si — et c'est la seule chose que
        // build() a besoin de savoir pour le dire.
        private boolean fichierDemande;
        private boolean formatDemande;
        // L'apparence de PlantUML : ne rien imposer est le défaut le moins présomptueux.
        private UmlStyle style = UmlStyle.PLANTUML;
        // Vide par défaut : le répertoire analysé se suffit quand ses classes ne mentionnent
        // que le JDK et les annotations de la bibliothèque.
        private ImmutableList<Path> classpath = ImmutableList.of();
        // Faux par défaut : le dossier décrit l'état courant du code, et une vue retirée du
        // fichier de règles ne laisse pas derrière elle une image que plus rien ne met à jour.
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
            // Vérifié ici plutôt qu'au scan : le message peut alors désigner l'appel fautif.
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
            // C'est l'extension qui décide du format ; le writer est seul juge de ce qu'il sait
            // produire, d'où la délégation.
            checkArgument(PlantUmlWriter.supporte(fileName),
                "The output format must be .png or .svg (got: %s)", fileName);

            this.outputFileName = fileName;
            this.fichierDemande = true;
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
            this.formatDemande = true;
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
         * <p>C'est ici que se juge la cohérence de l'ensemble, faute de pouvoir la juger appel par
         * appel : {@code outputFile} et {@code outputDirectory} demandent deux sorties différentes,
         * et {@code outputFormat} ne veut rien dire sans dossier. {@code generate()} tranchait
         * jusqu'ici en silence — le fichier nommé n'était jamais écrit, ou le format demandé jamais
         * lu —, ce qui laissait l'utilisateur devant un résultat qu'il n'avait pas demandé et sans
         * rien pour comprendre.
         *
         * @return le générateur, prêt à {@link UMLGenerator#generate()}.
         * @throws NullPointerException si le répertoire source n'a pas été fourni : il n'a pas de
         *     défaut raisonnable.
         * @throws IllegalStateException si deux réglages se contredisent : un fichier et un dossier
         *     de sortie demandés ensemble, ou un format d'image demandé hors du mode dossier.
         */
        public UMLGenerator build() {
            checkNotNull(sourceDirectory,
                "The source directory is mandatory. Use .sourceDirectory(...)");
            checkState(!(fichierDemande && outputDirectory != null),
                "Both an output file (%s) and an output directory (%s) are asked for, and only "
                    + "one of them can be written. Keep .outputFile(...) for the single "
                    + "whole-project diagram, or .outputDirectory(...) to get every view.",
                outputFileName, outputDirectory);
            checkState(!(formatDemande && outputDirectory == null),
                "An output format (%s) is asked for, but no output directory: in single-file mode "
                    + "the format is read from the file name. Write .outputFile(\"diagram%s\"), or "
                    + "ask for a directory with .outputDirectory(...).",
                extension, extension);
            return new UMLGenerator(this);
        }
    }
}
