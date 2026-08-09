package io.github.oliviercailloux.umlg.rendering;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.model.UmlView;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import net.sourceforge.plantuml.BlockUml;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.Diagram;
import net.sourceforge.plantuml.dot.GraphvizUtils;

// Du modèle à l'image sur le disque.
//
// Deux étapes : produire le texte PlantUML (délégué à PlantUmlSyntaxBuilder), puis le faire
// dessiner par la bibliothèque PlantUML. C'est le seul endroit qui écrive un fichier.
public class PlantUmlWriter {

    private static final String EXTENSION_SVG = ".svg";
    private static final String EXTENSION_PNG = ".png";

    // L'extension de la source PlantUML, conservée à côté de chaque image.
    //
    // Sans elle, la source engendrée était irrécupérable : impossible de retoucher un diagramme
    // à la main pour un essai, et le diff entre deux générations — la seule façon de voir ce
    // qu'un changement de code a changé au dessin — se faisait sur du SVG illisible. Le fichier
    // texte est ce qui se relit, se corrige et se versionne.
    private static final String EXTENSION_PUML = ".puml";

    // Côté maximal, en pixels, d'une image matricielle.
    //
    // PlantUML plafonne les siennes à 4096 et rogne au-delà, sans rien signaler : sur un
    // diagramme un peu large, des boîtes entières manquaient au PNG alors que le SVG — qui n'a
    // pas de limite, étant vectoriel — était complet. Un diagramme amputé en silence est le pire
    // des résultats, puisque rien ne le distingue d'un diagramme correct.
    //
    // 16384 est choisi assez haut pour qu'aucun diagramme de classes réaliste ne l'atteigne, et
    // reste néanmoins un garde-fou : la limite existe pour éviter qu'une image démesurée épuise
    // la mémoire, et la retirer tout à fait remplacerait un dessin tronqué par une panne.
    private static final int COTE_MAXIMAL_PNG = 16384;

    // Le sous-dossier où vont toutes les vues, sauf l'intégrale qui reste à la racine : c'est
    // elle qu'on ouvre en premier, et la chercher parmi vingt fichiers serait une corvée.
    private static final String DOSSIER_DES_VUES = "vues";

    // Le nom de fichier de la vue intégrale, quel que soit son titre.
    //
    // Fixe, et c'est le sujet : elle porte souvent un titre déclaré — « Architecture du module
    // Facturation » —, et nommer le fichier d'après lui ferait changer son chemin à chaque
    // retouche du titre, cassant les liens de toute documentation qui la montre. C'est le
    // diagramme qu'on ouvre en premier ; il doit se trouver toujours au même endroit.
    private static final String NOM_DE_L_INTEGRALE = "projet";

    private PlantUmlWriter() {}

    // Écrit tout ce que le projet décrit : la vue intégrale à la racine du dossier, les autres
    // dans un sous-dossier.
    //
    //   diagrammes/
    //   ├── projet.svg
    //   └── vues/
    //       ├── mapping.svg
    //       └── mnemonics.svg
    //
    // Rend la liste des fichiers écrits, dans l'ordre où ils l'ont été.
    public static ImmutableList<Path> writeViews(UmlProject project, Path dossier, String extension,
        UmlStyle style, Regles.Sortie sortie) throws Exception {
        checkNotNull(project);
        checkNotNull(dossier);
        checkNotNull(style);
        checkNotNull(sortie);
        checkArgument(supporte("x" + extension),
            "The output format must be .png or .svg (got: %s)", extension);
        checkArgument(!project.views().isEmpty(),
            "This project describes no view; nothing would be written.");

        Path racine = destination(dossier, sortie);
        Path desVues = racine.resolve(DOSSIER_DES_VUES);
        Files.createDirectories(desVues);

        ImmutableList.Builder<Path> ecrits = ImmutableList.builder();
        // La première vue est l'intégrale : ConstructeurDeVues la place toujours en tête.
        boolean integrale = true;

        // Où mène chaque boîte : calculé une fois pour tout le dossier, puis restreint à chaque
        // vue — une boîte ne renvoie jamais vers la vue qu'on est en train de lire.
        Map<String, String> detailleePar = detailleePar(project, extension);

        for (UmlView vue : project.views()) {
            Path fichier = integrale
                ? racine.resolve(NOM_DE_L_INTEGRALE + extension)
                : desVues.resolve(vue.nomDeFichier() + extension);

            write(vue, project.diagram(), fichier.toString(), style,
                navigation(vue, detailleePar, integrale, extension));
            // Le dessin est écrit, donc mesurable : c'est le seul moment où l'on peut dire à
            // l'utilisateur que sa vue est illisible avant qu'il n'ouvre le fichier.
            Proportions.verifier(fichier, vue.name());
            ecrits.add(fichier);
            integrale = false;
        }
        return ecrits.build();
    }

    // Le dossier où écrire cette génération.
    //
    // Sous REPLACE, c'est celui qu'on a demandé, vidé de ce qu'une génération précédente y aurait
    // laissé : sans cela, une vue retirée du fichier de règles laisserait derrière elle une image
    // que plus rien ne met à jour, et qui ment d'autant mieux qu'elle a l'air d'être à sa place.
    //
    // Sous KEEP, c'est un dossier voisin numéroté — « diagrammes-2 », « diagrammes-3 » —, ce qui
    // laisse la génération précédente intacte et permet de comparer les deux.
    private static Path destination(Path dossier, Regles.Sortie sortie) throws IOException {
        if (sortie == Regles.Sortie.REPLACE) {
            viderLesVues(dossier);
            return dossier;
        }
        if (!Files.exists(dossier)) {
            return dossier;
        }
        // Le premier numéro libre. La borne est un garde-fou : mille générations conservées
        // signalent un dossier qu'on a oublié de nettoyer, pas un besoin réel.
        for (int numero = 2; numero < 1000; numero++) {
            Path candidat = dossier.resolveSibling(dossier.getFileName() + "-" + numero);
            if (!Files.exists(candidat)) {
                return candidat;
            }
        }
        throw new IllegalStateException("Too many kept generations next to " + dossier
            + ". Remove the old ones, or switch to \"output replace\".");
    }

    // Efface les diagrammes de la génération précédente, et eux seuls.
    //
    // Seuls les fichiers que nous écrivons sont retirés — ceux qui portent nos extensions, dans
    // notre sous-dossier et à la racine du dossier de sortie. Un dossier de sortie qui
    // contiendrait autre chose, par mégarde ou parce que l'utilisateur l'a voulu, n'est pas vidé
    // de ce qui ne nous appartient pas.
    private static void viderLesVues(Path dossier) throws IOException {
        if (!Files.isDirectory(dossier)) {
            return;
        }
        for (Path racine : new Path[] {dossier, dossier.resolve(DOSSIER_DES_VUES)}) {
            if (!Files.isDirectory(racine)) {
                continue;
            }
            try (java.util.stream.Stream<Path> fichiers = Files.list(racine)) {
                for (Path fichier : fichiers.sorted(Comparator.naturalOrder()).toList()) {
                    String nom = fichier.getFileName().toString();
                    // Les sources .puml suivent le sort de leurs images : une source dont
                    // l'image a disparu décrirait un diagramme que rien ne dessine plus.
                    if (Files.isRegularFile(fichier)
                        && (supporte(nom) || nom.endsWith(EXTENSION_PUML))) {
                        Files.delete(fichier);
                    }
                }
            }
        }
    }

    // Une vue, dans son fichier. Les réglages d'ensemble du projet lui donnent le sort des
    // compartiments vides ; son titre et son sens de lecture, eux, sont les siens.
    private static void write(UmlView vue, UmlDiagram reglages, String fichier, UmlStyle style,
        Map<String, String> navigation) throws Exception {
        UmlDiagram propres =
            new UmlDiagram(vue.name(), vue.direction(), reglages.hideEmptyMembers());

        ecrire(new PlantUmlSyntaxBuilder(style, propres).navigation(navigation),
            vue.entities(), fichier);
    }

    // Quelle vue détaille chaque boîte : la première qui la retient, hors vue intégrale.
    //
    // Un dossier de onze diagrammes n'est qu'une collection d'images tant qu'on ne peut pas
    // passer de l'une à l'autre. Depuis la vue d'ensemble, cliquer sur une classe doit ouvrir
    // celle qui la détaille — c'est le geste qu'on fait naturellement, et le seul qui manque.
    private static Map<String, String> detailleePar(UmlProject project, String extension) {
        Map<String, String> vers = new LinkedHashMap<>();
        boolean integrale = true;

        for (UmlView vue : project.views()) {
            if (integrale) {
                // L'intégrale ne détaille rien : elle montre tout, c'est d'elle qu'on part.
                integrale = false;
                continue;
            }
            for (UmlEntity entite : vue.entities()) {
                vers.putIfAbsent(entite.qualifiedName(), vue.nomDeFichier() + extension);
            }
        }
        return vers;
    }

    // La table de navigation d'une vue donnée, avec les chemins vus depuis son propre fichier.
    //
    // Une boîte ne renvoie jamais vers la vue qu'on lit déjà : le lien ne mènerait nulle part et
    // laisserait croire qu'il y a autre chose à voir.
    private static Map<String, String> navigation(UmlView vue, Map<String, String> detailleePar,
        boolean integrale, String extension) {
        String propre = vue.nomDeFichier() + extension;
        Map<String, String> pourCetteVue = new LinkedHashMap<>();

        for (UmlEntity entite : vue.entities()) {
            String cible = detailleePar.get(entite.qualifiedName());
            if (cible == null || cible.equals(propre)) {
                continue;
            }
            // Depuis la racine, il faut descendre dans le sous-dossier ; depuis une vue, les
            // autres sont ses voisines.
            pourCetteVue.put(entite.qualifiedName(),
                integrale ? DOSSIER_DES_VUES + "/" + cible : cible);
        }
        return pourCetteVue;
    }

    // Le format voulu se lit dans l'extension du nom de fichier. Cette méthode permet à l'appelant
    // (UMLGenerator) de refuser une extension inconnue avant d'avoir rien calculé.
    public static boolean supporte(String outputFileName) {
        checkNotNull(outputFileName);
        // Locale.ROOT et non la locale par défaut : en turc, "I".toLowerCase() donne "ı", et le
        // test sur ".SVG" échouerait selon la machine.
        String normalise = outputFileName.toLowerCase(Locale.ROOT);
        return normalise.endsWith(EXTENSION_SVG) || normalise.endsWith(EXTENSION_PNG);
    }

    // PNG par défaut : n'est appelée qu'après supporte(), donc « pas SVG » veut dire PNG.
    private static FileFormat formatPour(String outputFileName) {
        return outputFileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION_SVG)
            ? FileFormat.SVG
            : FileFormat.PNG;
    }

    // L'apparence par défaut : celle de PlantUML. Un appelant qui ne demande rien obtient le
    // rendu habituel de l'outil, et non le thème de la bibliothèque — ne rien imposer est le
    // point de départ le moins présomptueux.
    public static void write(UmlProject project, String outputFileName) throws Exception {
        write(project, outputFileName, UmlStyle.PLANTUML);
    }

    public static void write(UmlProject project, String outputFileName, UmlStyle style)
        throws Exception {
        checkNotNull(project);
        checkNotNull(outputFileName);
        checkNotNull(style);

        // Deux sources gouvernent le fichier engendré, et elles ne viennent pas du même endroit :
        // le style est choisi ici, à l'appel du générateur, tandis que les réglages d'ensemble
        // viennent du fichier de règles, lu au scan avec le reste du projet.
        ecrire(new PlantUmlSyntaxBuilder(style, project.diagram()), project.entities(),
            outputFileName);

        // La même mesure qu'en mode dossier : le fichier unique est justement le mode de qui n'a
        // rien configuré, et c'est lui qui produisait des rubans sans que personne ne le dise.
        // Le message nomme le diagramme comme le nommerait la vue intégrale.
        Proportions.verifier(Path.of(outputFileName),
            project.diagram().aUnTitre() ? project.diagram().title() : outputFileName);
    }

    // Le tronc commun : des boîtes, un fichier.
    private static void ecrire(PlantUmlSyntaxBuilder syntax, Iterable<UmlEntity> entities,
        String outputFileName) throws Exception {
        syntax.startDocument();
        // Deux passes : une relation nomme ses extrémités par leur alias, qui n'existe qu'une
        // fois la classe déclarée. Une classe citée par une association déclarée trop tôt
        // serait recréée vide par PlantUML au lieu d'être reliée à la vraie boîte.
        //
        // Les boîtes sont déclarées d'un seul appel, et non une par une : c'est ce qui permet
        // d'ouvrir et de refermer au bon endroit les rectangles de @UMLPackage.
        syntax.addClasses(entities);
        // Deux attributs qui se font face ne sont qu'une association : la paire est fusionnée
        // avant d'être écrite, faute de quoi le dessin porterait deux traits parallèles là où
        // UML en veut un, à deux extrémités nommées.
        com.google.common.collect.ImmutableMap<String,
            ImmutableList<io.github.oliviercailloux.umlg.model.UmlRelation>> relations =
                AssociationsReciproques.de(entities);
        for (UmlEntity entity : entities) {
            syntax.addRelations(entity, relations.get(entity.qualifiedName()));
        }
        // Les notes en dernier : elles désignent les boîtes, et les grouper en fin de fichier
        // garde le texte engendré lisible — les traits d'un côté, les commentaires de l'autre.
        for (UmlEntity entity : entities) {
            syntax.addNotes(entity);
        }
        syntax.endDocument();

        String codePlantUml = syntax.build();

        FileFormat format = formatPour(outputFileName);

        // try-with-resources : le flux est fermé même si le rendu échoue.
        try (OutputStream os = new FileOutputStream(outputFileName)) {
            // SourceStringReader analyse le texte PlantUML ; outputImage le dessine et l'écrit.
            SourceStringReader reader = new SourceStringReader(codePlantUml);

            verifierAcceptation(reader, codePlantUml);
            verifierMoteurDeDessin();

            // La source acceptée est conservée à côté de l'image, sous le même nom : c'est elle
            // qu'on retouche pour un essai, et elle que deux générations comparent utilement.
            int point = outputFileName.lastIndexOf('.');
            Files.writeString(Path.of(outputFileName.substring(0, point) + EXTENSION_PUML),
                codePlantUml);

            // Le plafond est posé sur le fil courant — c'est une ThreadLocal chez PlantUML —,
            // puis retiré : la bibliothèque ne laisse pas derrière elle un réglage global que
            // l'appelant n'a pas demandé.
            GraphvizUtils.setLocalImageLimit(COTE_MAXIMAL_PNG);
            try {
                reader.outputImage(os, new FileFormatOption(format));
            } finally {
                GraphvizUtils.removeLocalLimitSize();
            }

        }
    }

    // Le texte engendré est-il une syntaxe que PlantUML accepte ?
    //
    // La question doit être posée avant de dessiner, car outputImage ne la pose pas : devant une
    // source fautive, PlantUML ne lève rien et écrit une image d'erreur, que le fichier produit
    // ne distingue en rien d'un diagramme réussi. L'utilisateur reçoit alors un « Success »,
    // ouvre son image, et y trouve un message rouge.
    //
    // Le cas ne devrait jamais se produire : cette source, c'est nous qui l'écrivons. D'où
    // IllegalStateException, qui dit ce qu'elle est — un défaut de la bibliothèque, pas une faute
    // de l'appelant. La source complète accompagne le message : sans elle, le rapport de bogue
    // serait indéchiffrable.
    private static void verifierAcceptation(SourceStringReader reader, String codePlantUml) {
        for (BlockUml bloc : reader.getBlocks()) {
            Diagram diagramme = bloc.getDiagram();
            String probleme = diagramme.getWarningOrError();

            if (probleme != null) {
                throw new IllegalStateException(
                    "The generated PlantUML source was rejected by the rendering engine ("
                        + diagramme.getClass().getSimpleName() + "): " + probleme.strip()
                        + "\n--- rejected source ---\n" + codePlantUml);
            }
        }
    }

    // Le moteur de dessin est-il en état de dessiner ?
    //
    // La garde précédente couvre la source ; celle-ci couvre l'environnement, et c'est un tout
    // autre défaut. Un diagramme de classes est mis en page par Graphviz, que PlantUML appelle en
    // sous-processus. Quand il manque, PlantUML ne lève rien et ne rapporte aucune erreur de
    // diagramme : il dessine une image portant « Cannot find Graphviz », que rien ne distingue
    // d'un dessin réussi — ni sa taille, ni son format. Le compte rendu annonçait alors un succès.
    //
    // La vérification est faite une seule fois par JVM : elle lance « dot -V », et le faire à
    // chaque vue coûterait un sous-processus par diagramme.
    private static void verifierMoteurDeDessin() {
        if (MoteurDeDessin.UTILISABLE) {
            return;
        }
        throw new IllegalStateException("Graphviz is not usable, and a class diagram cannot be "
            + "laid out without it: " + String.join(" / ", MoteurDeDessin.DIAGNOSTIC)
            + ". Install Graphviz (\"apt install graphviz\", \"brew install graphviz\"), or point "
            + "the GRAPHVIZ_DOT environment variable at the dot executable. Without this check "
            + "the generation would have written an image carrying an error message, and reported "
            + "it as a success.");
    }

    // L'état de Graphviz, calculé au premier besoin et retenu.
    //
    // Une classe imbriquée plutôt qu'un champ statique de PlantUmlWriter : l'initialisation
    // paresseuse est alors celle de la JVM, sans verrou à écrire, et le sous-processus n'est
    // lancé que si une image est réellement demandée — un appelant qui n'utilise que la syntaxe
    // n'a pas à payer pour un moteur qu'il n'appelle pas.
    private static final class MoteurDeDessin {

        private static final ImmutableList<String> DIAGNOSTIC;
        private static final boolean UTILISABLE;

        static {
            java.util.List<String> lignes = new java.util.ArrayList<>();
            // Le même contrôle que « java -jar plantuml.jar -testdot » : 0 quand tout va bien.
            // Le second argument demande un diagnostic en texte nu, sans balisage PlantUML.
            int statut = GraphvizUtils.addDotStatus(lignes, false);
            UTILISABLE = statut == 0;
            DIAGNOSTIC = ImmutableList.copyOf(lignes);
        }

        private MoteurDeDessin() {}
    }
}
