import io.github.oliviercailloux.umlg.UMLGenerator;

public class Main {
    public static void main(String[] args) {
        UMLGenerator.builder()
            .sourceDirectory("target/classes")
            .outputFile("architecture_banque.svg")
            .build()
            .generate();
    }
}
