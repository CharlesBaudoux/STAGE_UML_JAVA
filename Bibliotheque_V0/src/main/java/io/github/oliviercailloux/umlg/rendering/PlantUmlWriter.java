package io.github.oliviercailloux.umlg.rendering;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.io.FileOutputStream;
import java.io.OutputStream;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

public final class PlantUmlWriter {

    private PlantUmlWriter() {}

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

        
        FileFormat format = outputFileName.toLowerCase().endsWith(".svg") 
            ? FileFormat.SVG 
            : FileFormat.PNG;

        try (OutputStream os = new FileOutputStream(outputFileName)) {
            SourceStringReader reader = new SourceStringReader(codePlantUml);
            
            
            reader.outputImage(os, new FileFormatOption(format));
            
            System.out.println("Succès : Diagramme généré -> " + outputFileName);
        }
    }
}