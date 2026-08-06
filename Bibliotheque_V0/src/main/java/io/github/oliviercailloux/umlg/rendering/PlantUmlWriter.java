package io.github.oliviercailloux.umlg.rendering;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.io.FileOutputStream;
import java.io.OutputStream;
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

    private PlantUmlWriter() {}

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
        // ont été lus dans le code annoté, sur le package-info du projet.
        PlantUmlSyntaxBuilder syntax = new PlantUmlSyntaxBuilder(style, project.diagram());

        syntax.startDocument();
        // Deux passes : une relation nomme ses extrémités par leur alias, qui n'existe qu'une
        // fois la classe déclarée. Une classe citée par une association déclarée trop tôt
        // serait recréée vide par PlantUML au lieu d'être reliée à la vraie boîte.
        //
        // Les boîtes sont déclarées d'un seul appel, et non une par une : c'est ce qui permet
        // d'ouvrir et de refermer au bon endroit les rectangles de @UMLPackage.
        syntax.addClasses(project.entities());
        for (UmlEntity entity : project.entities()) {
            syntax.addRelations(entity);
        }
        // Les notes en dernier : elles désignent les boîtes, et les grouper en fin de fichier
        // garde le texte engendré lisible — les traits d'un côté, les commentaires de l'autre.
        for (UmlEntity entity : project.entities()) {
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

            // Le plafond est posé sur le fil courant — c'est une ThreadLocal chez PlantUML —,
            // puis retiré : la bibliothèque ne laisse pas derrière elle un réglage global que
            // l'appelant n'a pas demandé.
            GraphvizUtils.setLocalImageLimit(COTE_MAXIMAL_PNG);
            try {
                reader.outputImage(os, new FileFormatOption(format));
            } finally {
                GraphvizUtils.removeLocalLimitSize();
            }

            System.out.println("Success: diagram generated -> " + outputFileName);
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
}
