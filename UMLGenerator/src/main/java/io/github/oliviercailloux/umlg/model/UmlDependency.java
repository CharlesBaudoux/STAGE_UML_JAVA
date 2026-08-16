package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A class that uses another without holding it, drawn as a dotted arrow.
 *
 * @param targetQualifiedName the qualified name of the class depended upon.
 * @param label the text written along the arrow.
 */
public record UmlDependency(String targetQualifiedName, String label) implements UmlRelation {
    /** Checks that the target is named and that the text is not null. */
    public UmlDependency {
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(label, "The label cannot be null (use an empty string).");

        checkArgument(!targetQualifiedName.isBlank(),
            "The target qualified name cannot be empty.");

        checkArgument(label.isEmpty() || !label.isBlank(),
            "The label cannot be made of whitespace only.");
    }

    /**
     * A dependency whose arrow carries no text.
     *
     * @param targetQualifiedName the qualified name of the class depended upon.
     * @return the dependency, with no text along the arrow.
     */
    public static UmlDependency toTarget(String targetQualifiedName) {
        return new UmlDependency(targetQualifiedName, "");
    }
}
