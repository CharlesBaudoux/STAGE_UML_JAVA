package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLFlow;

/**
 * The settings that hold for the whole drawing.
 *
 * @param title the title written at the top, empty if there is none.
 * @param direction the direction in which the drawing unfolds.
 * @param hideEmptyMembers true to silence the compartments left empty.
 */
public record UmlDiagram(String title, UMLFlow direction, boolean hideEmptyMembers) {
    private static final UmlDiagram Default = new UmlDiagram("", UMLFlow.TOP_TO_BOTTOM, true);

    /** Checks that the title and the reading direction are not null. */
    public UmlDiagram {
        checkNotNull(title, "The title cannot be null; use the empty string for no title.");
        checkNotNull(direction, "The direction cannot be null; use UMLFlow.TOP_TO_BOTTOM.");

        checkArgument(title.isEmpty() || !title.isBlank(),
            "The title cannot be made of whitespace only; leave it empty for no title.");
    }

    /**
     * {@return the settings of a project that asked for none: no title, top to bottom, *
     * empty compartments hidden}
     */
    public static UmlDiagram defaults() {
        return Default;
    }

    /** {@return true if a title is written at the head of the drawing} */
    public boolean hasTitle() {
        return !title.isEmpty();
    }
}
