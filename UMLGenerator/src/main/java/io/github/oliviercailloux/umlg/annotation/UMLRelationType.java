package io.github.oliviercailloux.umlg.annotation;

/**
 * The three natures an &#64;UMLAssociation can take, which differ by the strength of
 * ownership.
 */
public enum UMLRelationType {
    /** The two classes know each other, neither owns the other: bare stroke. */
    ASSOCIATION,

    /** The target does not outlive the source: filled diamond on the source side. */
    COMPOSITION,

    /** The target exists on its own and is merely grouped: hollow diamond on the source side. */
    AGGREGATION
}
