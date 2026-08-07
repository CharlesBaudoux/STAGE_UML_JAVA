package io.github.oliviercailloux.umlg.annotation;

// Les trois natures de lien qu'une @UMLAssociation peut prendre.
//
// Elles diffèrent par la force du lien de possession, et se traduisent par trois traits
// différents (voir RelationRenderer).
public enum UMLRelationType {

    ASSOCIATION,

    COMPOSITION,

    AGGREGATION
}
