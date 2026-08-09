package io.github.oliviercailloux.umlg.annotation;

/**
 * Les trois natures de lien qu'une @UMLAssociation peut prendre.
 *
 * <p>Elles diffèrent par la force du lien de possession, et se traduisent par trois traits
 * différents (voir RelationRenderer).
 */
public enum UMLRelationType {

    // Lien simple : les deux classes se connaissent, aucune ne possède l'autre. Trait nu.
    ASSOCIATION,

    // Possession forte : la cible ne survit pas à la source. Losange plein côté source.
    COMPOSITION,

    // Possession faible : la cible existe indépendamment, elle est seulement regroupée par la
    // source. Losange creux côté source.
    AGGREGATION
}
