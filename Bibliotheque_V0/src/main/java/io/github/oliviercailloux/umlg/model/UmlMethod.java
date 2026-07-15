package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

public record UmlMethod(
    String name,
    String returnType,
    String visibility,
    boolean isStatic,
    List<String> parameterTypes
) {
    public UmlMethod {
        parameterTypes = List.copyOf(
            checkNotNull(parameterTypes, "La liste des types de paramètres ne peut pas être null."));
    }
}