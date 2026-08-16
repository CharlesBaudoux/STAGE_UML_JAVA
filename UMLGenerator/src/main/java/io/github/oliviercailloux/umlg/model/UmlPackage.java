package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The named rectangle surrounding a group of boxes.
 *
 * @param name the name written on the rectangle, empty when the box is in no group.
 * @param color the background colour of the rectangle.
 */
public record UmlPackage(String name, String color) {
    private static final UmlPackage No = new UmlPackage("", "");

    /** Checks that the name and the colour are not null. */
    public UmlPackage {
        checkNotNull(name, "The group name cannot be null (use an empty string for no group).");
        checkNotNull(color, "The group color cannot be null (use an empty string).");

        checkArgument(name.isEmpty() || !name.isBlank(),
            "The group name cannot be made of whitespace only.");
        checkArgument(color.isEmpty() || !color.isBlank(),
            "The group color cannot be made of whitespace only.");

        checkArgument(!name.isEmpty() || color.isEmpty(),
            "A group color was given without a group name; there would be nothing to paint.");
    }

    /** {@return the absence of a group, which is the ordinary case} */
    public static UmlPackage none() {
        return No;
    }

    /** {@return true if the box is filed in a rectangle} */
    public boolean isGrouped() {
        return !name.isEmpty();
    }
}
