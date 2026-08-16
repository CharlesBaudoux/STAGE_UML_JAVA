package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An inheritance, or the realization of an interface.
 *
 * @param targetQualifiedName the qualified name of the parent class or interface.
 * @param type generalization or realization.
 */
public record UmlInheritance(String targetQualifiedName, UmlInheritanceType type)
    implements UmlRelation {
    /** Checks that the parent is named and that the nature is given. */
    public UmlInheritance {
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(type, "The inheritance type cannot be null.");
        checkArgument(!targetQualifiedName.isBlank(), "The target qualified name cannot be empty.");
    }

    /** {@return nothing: an inheritance stroke carries no text} */
    @Override
    public String label() {
        return "";
    }

    /**
     * A class that extends another.
     *
     * @param targetQualifiedName the qualified name of the parent class.
     * @return the class inheritance.
     */
    public static UmlInheritance generalization(String targetQualifiedName) {
        return new UmlInheritance(targetQualifiedName, UmlInheritanceType.GENERALIZATION);
    }

    /**
     * A class that realizes an interface.
     *
     * @param targetQualifiedName the qualified name of the realized interface.
     * @return the interface realization.
     */
    public static UmlInheritance realization(String targetQualifiedName) {
        return new UmlInheritance(targetQualifiedName, UmlInheritanceType.REALIZATION);
    }
}
