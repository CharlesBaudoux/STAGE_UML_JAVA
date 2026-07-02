package fr.dauphine.uml;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import net.sourceforge.plantuml.SourceStringReader;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;

import java.io.FileOutputStream;
import java.io.OutputStream;

public class GenerateurSpoonV0 {

    
    public static record SpoonUmlClass(String className, ImmutableList<String> attributes) {
        public SpoonUmlClass {
            
            Preconditions.checkNotNull(className, "Le nom de la classe ne peut pas être null.");
            Preconditions.checkArgument(!className.isBlank(), "Le nom de la classe ne peut pas être vide.");
            Preconditions.checkNotNull(attributes, "La liste des attributs ne peut pas être null.");
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage : mvn exec:java -Dexec.args=\"chemin/du/fichier.java\"");
            return;
        }

        try {
            
            CtClass<?> targetClass = parseJavaFile(args[0]);
            SpoonUmlClass umlModel = extractModel(targetClass);
            generatePng(umlModel);
        } catch (Exception e) {
            System.err.println("Erreur critique durant l'analyse : " + e.getMessage());
            e.printStackTrace();
        }
    }

    
    private static CtClass<?> parseJavaFile(String sourcePath) {
        Preconditions.checkNotNull(sourcePath, "Le chemin source est requis.");

        Launcher launcher = new Launcher();
        launcher.addInputResource(sourcePath);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();

        return launcher.getModel()
                .getElements(element -> element instanceof CtClass<?> && element.getAnnotation(UMLClass.class) != null)
                .stream()
                .map(e -> (CtClass<?>) e)
                .findFirst()  //renvoie un objet Optionnal, au cas ou ca ne renvoie rien
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucune classe valide avec @UMLClass trouvée dans : " + sourcePath
                ));
    }

    
    private static SpoonUmlClass extractModel(CtClass<?> targetClass) {
        
        ImmutableList<String> emptyAttributes = ImmutableList.of();
        return new SpoonUmlClass(targetClass.getSimpleName(), emptyAttributes);
    }

    
    private static void generatePng(SpoonUmlClass umlClass) throws Exception {
        StringBuilder source = new StringBuilder();
        source.append("@startuml\n");
        source.append("class ").append(umlClass.className()).append(" {\n");
        source.append("}\n");
        source.append("@enduml\n");

        String outputFileName = umlClass.className() + ".png";
        
        try (OutputStream png = new FileOutputStream(outputFileName)) {
            SourceStringReader reader = new SourceStringReader(source.toString());
            reader.generateImage(png);
            System.out.println("Succès : Graphique généré -> " + outputFileName);
        }
    }
}