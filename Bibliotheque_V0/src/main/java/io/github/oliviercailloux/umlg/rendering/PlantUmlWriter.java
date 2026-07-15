package io.github.oliviercailloux.umlg.rendering;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

public class PlantUmlWriter {

    private static final String EXTENSION_SVG = ".svg";
    private static final String EXTENSION_PNG = ".png";

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
        checkNotNull(project);
        checkNotNull(outputFileName);

        PlantUmlSyntaxBuilder syntax = new PlantUmlSyntaxBuilder();
        
        syntax.startDocument();
        for (UmlEntity entity : project.entities()) {
            syntax.addClass(entity); 
        }
        syntax.endDocument();

        String codePlantUml = syntax.build();

        FileFormat format = formatPour(outputFileName);

        try (OutputStream os = new FileOutputStream(outputFileName)) {
            SourceStringReader reader = new SourceStringReader(codePlantUml);
            
            
            reader.outputImage(os, new FileFormatOption(format));
            
            System.out.println("Succès : Diagramme généré -> " + outputFileName);
        }
    }
}