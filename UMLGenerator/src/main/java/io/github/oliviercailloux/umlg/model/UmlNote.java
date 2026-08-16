package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;

/**
 * A comment bubble attached to a box or to one of its members.
 *
 * @param text the content of the bubble.
 * @param direction the side it is placed on.
 * @param anchor the member it attaches to, empty for the whole box.
 */
public record UmlNote(
    String text,
    UMLDirection direction,
    String anchor
) {
    /** Checks that the text is not empty and that the anchor is not made of spaces. */
    public UmlNote {
        checkNotNull(text, "The note text cannot be null.");
        checkNotNull(direction, "The note direction cannot be null (use UMLDirection.NONE).");
        checkNotNull(anchor, "The note anchor cannot be null (use an empty string).");

        checkArgument(!text.isBlank(),
            "The note text cannot be empty or made of whitespace only.");
        checkArgument(anchor.isEmpty() || !anchor.isBlank(),
            "The note anchor cannot be made of whitespace only.");
    }

    /**
     * A bubble attached to the whole box, and not to one of its members.
     *
     * @param text the content of the bubble.
     * @param direction the side it is placed on.
     * @return a bubble attached to the whole box.
     */
    public static UmlNote onClass(String text, UMLDirection direction) {
        return new UmlNote(text, direction, "");
    }

    /** {@return true if the bubble attaches to a member rather than to the box} */
    public boolean isOnMember() {
        return !anchor.isEmpty();
    }
}
