package io.github.oliviercailloux.umlg.annotation;

/**
 * A side, where to push the element placed relative to the one carrying it: layout only, with no
 * meaning UML.
 */
public enum UMLDirection {
    /** No constraint: PlantUML chooses alone, and that is usually the right choice. */
    NONE,

    /** Above. */
    UP,

    /** En dessous. */
    DOWN,

    /** À gauche. */
    LEFT,

    /** On the right. */
    RIGHT
}
