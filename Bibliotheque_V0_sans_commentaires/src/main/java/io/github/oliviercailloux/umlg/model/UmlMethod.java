package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;

// Une ligne du compartiment des méthodes, par exemple "+ retirer(double) : void".
public record UmlMethod(
    String name,
    String returnType,
    String visibility,
    boolean isStatic,
    ImmutableList<String> parameterTypes,
    boolean derived
) {
    public UmlMethod {
        parameterTypes = ImmutableList.copyOf(
            checkNotNull(parameterTypes, "The list of parameter types cannot be null."));
    }

    public UmlMethod(String name, String returnType, String visibility, boolean isStatic,
        List<String> parameterTypes) {
        this(name, returnType, visibility, isStatic,
            ImmutableList.copyOf(checkNotNull(parameterTypes,
                "The list of parameter types cannot be null.")),
            false);
    }
}
