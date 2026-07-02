import fr.dauphine.uml.UmlGenerator;

public class Main {
    public static void main(String[] args) {
        System.out.println("Test de l'arborescence complexe...");
        UmlGenerator.builder()
            .sourceDirectory("target/classes")
            .outputFile("architecture_complexe.png")
            .build()
            .generate();
    }
}
