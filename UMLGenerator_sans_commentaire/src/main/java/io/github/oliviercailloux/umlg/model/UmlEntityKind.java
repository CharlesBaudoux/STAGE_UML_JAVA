package io.github.oliviercailloux.umlg.model;

/**
 * Ce que la boîte est : classe, interface, énumération…
 *
 * <p>Cette nature est déduite du code par réflexion, jamais écrite par l'utilisateur. C'est
 * pourquoi l'énumération vit dans le modèle et non dans le paquetage {@code annotation}, qui ne
 * contient que le vocabulaire que l'utilisateur écrit lui-même.
 *
 * <p>Un {@code record} n'a pas de valeur propre : c'est une classe finale, et UML ne lui réserve
 * aucune notation. Il est donc rendu comme {@link #CLASS}, ce qui n'est pas un pis-aller mais la
 * description exacte de ce qu'il est.
 */
public enum UmlEntityKind {

    /** Une classe ordinaire, et aussi un {@code record}. */
    CLASS("class"),

    /**
     * Une classe abstraite. Le nom est alors mis en italique, comme le veut la norme UML.
     */
    ABSTRACT_CLASS("abstract class"),

    /** Une interface. */
    INTERFACE("interface"),

    /** Une énumération, dont les littéraux occupent leur propre compartiment. */
    ENUM("enum"),

    /**
     * Un type annotation ({@code @interface}). Pour la réflexion ce sont des interfaces, mais
     * PlantUML sait les dessiner à part, et les confondre effacerait une information.
     */
    ANNOTATION("annotation");

    private final String motCle;

    UmlEntityKind(String motCle) {
        this.motCle = motCle;
    }

    /**
     * Le mot-clé de déclaration correspondant.
     *
     * @return le mot par lequel commence la déclaration de la boîte dans le fichier PlantUML.
     */
    public String keyword() {
        return motCle;
    }
}
