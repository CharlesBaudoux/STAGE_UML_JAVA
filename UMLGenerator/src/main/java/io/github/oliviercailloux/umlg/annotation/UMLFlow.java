package io.github.oliviercailloux.umlg.annotation;

/**
 * The direction in which the whole diagram unfolds, not to be confused with {@link UMLDirection},
 * which places one box relative to another.
 */
public enum UMLFlow {
    /** Inheritances go down and the drawing grows towards the bottom: the default. */
    TOP_TO_BOTTOM,

    /**
     * The drawing unfolds to the right, which reads better when the boxes are many and
     * shallow.
     */
    LEFT_TO_RIGHT
}
