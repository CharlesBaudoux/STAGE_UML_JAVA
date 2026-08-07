import io.github.oliviercailloux.umlg.UMLGenerator;

/**
 * Produit le diagramme dans les deux formats que le writer sait écrire : le SVG, dont le texte
 * se relit à la main, et le PNG, qui se regarde.
 */
public class Main {
    public static void main(String[] args) {
        for (String sortie : new String[] {"architecture_banque.svg", "architecture_banque.png"}) {
            UMLGenerator.builder()
                .sourceDirectory("target/classes")
                .outputFile(sortie)
                .build()
                .generate();
        }
    }
}
