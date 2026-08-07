package io.github.oliviercailloux.umlg.model;

// Ce que la boîte est : classe, interface, énumération…
//
// Cette nature est déduite du code par réflexion, jamais écrite par l'utilisateur. C'est
// pourquoi l'énumération vit dans le modèle et non dans le paquetage annotation, qui ne
// contient que le vocabulaire que l'utilisateur écrit lui-même.
//
// Un record n'a pas de valeur propre : c'est une classe finale, et UML ne lui réserve aucune
// notation. Il est donc rendu comme CLASS, ce qui n'est pas un pis-aller mais la description
// exacte de ce qu'il est.
public enum UmlEntityKind {

    CLASS("class"),

    ABSTRACT_CLASS("abstract class"),

    INTERFACE("interface"),

    ENUM("enum"),

    ANNOTATION("annotation");

    private final String motCle;

    UmlEntityKind(String motCle) {
        this.motCle = motCle;
    }

    public String motCle() {
        return motCle;
    }
}
