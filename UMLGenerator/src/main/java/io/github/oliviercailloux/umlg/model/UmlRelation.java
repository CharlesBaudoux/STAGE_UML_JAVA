package io.github.oliviercailloux.umlg.model;

/**
 * What links two boxes: association, association class, dependency, inheritance or placement
 * constraint.
 */
public sealed interface UmlRelation
    permits UmlAssociation, UmlAssociationClass, UmlDependency, UmlInheritance, UmlLayout {
    /** {@return the qualified name of the class at the other end} */
    String targetQualifiedName();

    /** {@return the text written along the stroke, empty if there is none} */
    String label();
}
