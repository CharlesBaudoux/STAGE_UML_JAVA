package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;

/**
 * A line of the field compartment.
 *
 * @param name the name of the field.
 * @param type the displayed type.
 * @param multiplicity the multiplicity written between brackets, empty if there is none.
 * @param visibility the symbol +, -, # or ~, empty to write none.
 * @param isStatic true for a class field, underlined on the drawing.
 * @param derived true if the field is computed: its name takes the slash.
 * @param constraints the rules written between braces after the line.
 */
public record UmlField(
    String name,
    String type,
    String multiplicity,
    String visibility,
    boolean isStatic,
    boolean derived,
    ImmutableList<String> constraints
) {
    private static final ImmutableList<String> Visibilities =
        ImmutableList.of("+", "-", "#", "~", "");

    /** Checks the name, the type, the multiplicity and the visibility symbol. */
    public UmlField {
        checkName(name);
        checkNotNull(type, "The field type cannot be null.");
        checkArgument(!type.isBlank(), "The type of field \"%s\" cannot be empty.", name);
        checkNotNull(multiplicity, "The multiplicity cannot be null (use an empty string).");
        Multiplicities.validate(multiplicity, "multiplicity");
        checkVisibility(visibility, name);
        constraints = Constraints.validated(constraints);
    }

    static void checkName(String name) {
        checkNotNull(name, "The member name cannot be null.");
        checkArgument(!name.isBlank(), "The member name cannot be empty.");
    }

    static void checkVisibility(String visibility, String name) {
        checkNotNull(visibility, "The visibility cannot be null (use an empty string).");
        checkArgument(Visibilities.contains(visibility),
            "The visibility of \"%s\" must be one of + - # ~, or empty to write none (got: \"%s\").",
            name, visibility);
    }

    /**
     * A field with no multiplicity, no slash and no constraint.
     *
     * @param name the name of the field.
     * @param type the displayed type.
     * @param visibility the symbol +, -, # or ~.
     * @param isStatic true for a class field.
     */
    public UmlField(String name, String type, String visibility, boolean isStatic) {
        this(name, type, "", visibility, isStatic, false, ImmutableList.of());
    }
}
