package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;

/**
 * A placement constraint, with no UML meaning: it pushes a box to one side.
 *
 * @param targetQualifiedName the qualified name of the box to place.
 * @param direction the side to push it to.
 */
public record UmlLayout(String targetQualifiedName, UMLDirection direction)
    implements UmlRelation {
    /** Checks that the box aimed at is named and that the side is given. */
    public UmlLayout {
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(direction, "The direction cannot be null (use UMLDirection.NONE).");

        checkArgument(!targetQualifiedName.isBlank(),
            "The target qualified name cannot be empty.");
    }

    /** {@return nothing: a placement draws no text} */
    @Override
    public String label() {
        return "";
    }
}
