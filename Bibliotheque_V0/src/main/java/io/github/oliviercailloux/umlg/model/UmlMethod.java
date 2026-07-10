package io.github.oliviercailloux.umlg.model;

import java.util.List;

public record UmlMethod(
    String name,
    String returnType,
    String visibility,
    boolean isStatic,
    List<String> parameterTypes 
) {
}