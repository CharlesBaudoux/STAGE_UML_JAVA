package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A parameter of a method.
 *
 * @param name the name of the parameter, empty if it is not known.
 * @param type the displayed type.
 */
public record UmlParameter(
    String name,
    String type
) {
    /** Checks that the type is written and that the name, if any, is not made of spaces. */
    public UmlParameter {
        checkNotNull(name, "The parameter name cannot be null (use an empty string).");
        checkNotNull(type, "The parameter type cannot be null.");

        checkArgument(!type.isBlank(), "The parameter type cannot be empty.");
        checkArgument(name.isEmpty() || !name.isBlank(),
            "The parameter name cannot be made of whitespace only.");
    }

    /**
     * A parameter reduced to its type, its name being absent from the .class files.
     *
     * @param type the displayed type.
     * @return a parameter reduced to its type, its name being absent from the .class files.
     */
    public static UmlParameter unnamed(String type) {
        return new UmlParameter("", type);
    }

    /** {@return true if the parameter carries a name} */
    public boolean isNamed() {
        return !name.isEmpty();
    }
}
