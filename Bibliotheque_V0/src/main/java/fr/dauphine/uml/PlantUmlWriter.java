package fr.dauphine.uml;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Optional;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

final class PlantUmlWriter {

    private PlantUmlWriter() {}

    static void write(UmlProject project, String outputFileName) throws Exception {
        checkNotNull(project);
        checkNotNull(outputFileName);

        PlantUmlSyntaxBuilder syntax = new PlantUmlSyntaxBuilder();
        
        syntax.startDocument();
        for (UmlEntity entity : project.entities()) {
            syntax.addClass(entity.name(), Optional.empty())
                  .endClass();
        }
        syntax.endDocument();

        String codePlantUml = syntax.build();

        
        FileFormat format = outputFileName.toLowerCase().endsWith(".svg") 
            ? FileFormat.SVG 
            : FileFormat.PNG;

        try (OutputStream os = new FileOutputStream(outputFileName)) {
            SourceStringReader reader = new SourceStringReader(codePlantUml);
            
            
            reader.generateImage(os, new FileFormatOption(format));
            
            System.out.println("Succès : Diagramme généré -> " + outputFileName);
        }
    }
}