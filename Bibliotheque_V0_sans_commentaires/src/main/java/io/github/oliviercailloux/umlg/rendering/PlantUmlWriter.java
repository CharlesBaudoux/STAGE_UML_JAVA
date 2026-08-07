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

    private static final int COTE_MAXIMAL_PNG = 16384;

    private PlantUmlWriter() {}

    public static boolean supporte(String outputFileName) {
        checkNotNull(outputFileName);
        String normalise = outputFileName.toLowerCase(Locale.ROOT);
        return normalise.endsWith(EXTENSION_SVG) || normalise.endsWith(EXTENSION_PNG);
    }

    private static FileFormat formatPour(String outputFileName) {
        return outputFileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION_SVG)
            ? FileFormat.SVG
            : FileFormat.PNG;
    }

    public static void write(UmlProject project, String outputFileName) throws Exception {
        write(project, outputFileName, UmlStyle.PLANTUML);
    }

    public static void write(UmlProject project, String outputFileName, UmlStyle style)
        throws Exception {
        checkNotNull(project);
        checkNotNull(outputFileName);
        checkNotNull(style);

        PlantUmlSyntaxBuilder syntax = new PlantUmlSyntaxBuilder(style);

        syntax.startDocument();
        for (UmlEntity entity : project.entities()) {
            syntax.addClass(entity);
        }
        for (UmlEntity entity : project.entities()) {
            syntax.addRelations(entity);
        }
        for (UmlEntity entity : project.entities()) {
            syntax.addNotes(entity);
        }
        syntax.endDocument();

        String codePlantUml = syntax.build();

        FileFormat format = formatPour(outputFileName);

        try (OutputStream os = new FileOutputStream(outputFileName)) {
            SourceStringReader reader = new SourceStringReader(codePlantUml);

            verifierAcceptation(reader, codePlantUml);

            GraphvizUtils.setLocalImageLimit(COTE_MAXIMAL_PNG);
            try {
                reader.outputImage(os, new FileFormatOption(format));
            } finally {
                GraphvizUtils.removeLocalLimitSize();
            }

            System.out.println("Success: diagram generated -> " + outputFileName);
        }
    }

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
