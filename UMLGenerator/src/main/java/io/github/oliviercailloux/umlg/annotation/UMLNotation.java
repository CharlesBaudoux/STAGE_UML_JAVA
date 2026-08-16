package io.github.oliviercailloux.umlg.annotation;

/** The two ways of drawing one same association; UML allows only one at a time. */
public enum UMLNotation {
    /** A stroke between the two boxes, decorated at its ends: the default. */
    ARROW,

    /** A line of the field compartment, and no stroke towards the target. */
    ATTRIBUTE
}
