package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A line of the method compartment.
 *
 * @param name the name of the method.
 * @param returnType the returned type, empty to write none.
 * @param multiplicity the multiplicity of the returned value.
 * @param visibility the symbol +, -, # or ~, empty to write none.
 * @param isStatic true for a class method, underlined on the drawing.
 * @param parameters the parameters, in signature order.
 * @param derived true if the method is derived: its name takes the slash.
 * @param isAbstract true for an operation with no implementation, written in italics.
 */
public record UmlMethod(
    String name,
    String returnType,
    String multiplicity,
    String visibility,
    boolean isStatic,
    ImmutableList<UmlParameter> parameters,
    boolean derived,
    boolean isAbstract
) {
    /** Checks the name, the returned type, the multiplicity and the visibility symbol. */
    public UmlMethod {
        UmlField.checkName(name);
        checkNotNull(returnType, "The return type cannot be null (use an empty string).");
        checkArgument(returnType.isEmpty() || !returnType.isBlank(),
            "The return type of \"%s\" cannot be made of whitespace only.", name);
        checkNotNull(multiplicity, "The multiplicity cannot be null (use an empty string).");
        Multiplicities.validate(multiplicity, "multiplicity");
        UmlField.checkVisibility(visibility, name);

        parameters = ImmutableList.copyOf(
            checkNotNull(parameters, "The list of parameters cannot be null."));
    }

    /**
     * A method not known to be abstract or not.
     *
     * @param name the name of the method.
     * @param returnType the returned type.
     * @param multiplicity the multiplicity of the returned value.
     * @param visibility the symbol +, -, # or ~.
     * @param isStatic true for a class method.
     * @param parameters the parameters, in signature order.
     * @param derived true if the method is derived.
     */
    public UmlMethod(String name, String returnType, String multiplicity, String visibility,
        boolean isStatic, ImmutableList<UmlParameter> parameters, boolean derived) {
        this(name, returnType, multiplicity, visibility, isStatic, parameters, derived, false);
    }

    /**
     * A method whose parameters carry their type only.
     *
     * @param name the name of the method.
     * @param returnType the returned type.
     * @param visibility the symbol +, -, # or ~.
     * @param isStatic true for a class method.
     * @param parameterTypes the parameter types, in signature order.
     */
    public UmlMethod(String name, String returnType, String visibility, boolean isStatic,
        List<String> parameterTypes) {
        this(name, returnType, "", visibility, isStatic,
            checkNotNull(parameterTypes, "The list of parameter types cannot be null.").stream()
                .map(UmlParameter::unnamed)
                .collect(ImmutableList.toImmutableList()),
            false, false);
    }
}
