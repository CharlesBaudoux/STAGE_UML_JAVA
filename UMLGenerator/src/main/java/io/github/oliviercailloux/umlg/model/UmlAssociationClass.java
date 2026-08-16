package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A class carrying the fields of the link between two others.
 *
 * @param sourceQualifiedName the qualified name of the class at one endpoint.
 * @param targetQualifiedName the qualified name of the class at the other end.
 */
public record UmlAssociationClass(String sourceQualifiedName, String targetQualifiedName)
    implements UmlRelation {
    /** Checks that both endpoints are named. */
    public UmlAssociationClass {
        checkNotNull(sourceQualifiedName, "The source qualified name cannot be null.");
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");

        checkArgument(!sourceQualifiedName.isBlank(),
            "The source qualified name cannot be empty.");
        checkArgument(!targetQualifiedName.isBlank(),
            "The target qualified name cannot be empty.");
    }

    /** {@return nothing: the stroke of an association class carries no text} */
    @Override
    public String label() {
        return "";
    }
}
