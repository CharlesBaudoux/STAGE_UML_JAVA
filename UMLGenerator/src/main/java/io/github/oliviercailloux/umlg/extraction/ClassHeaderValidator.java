package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

public final class ClassHeaderValidator {
    private ClassHeaderValidator() {}

    static void validateName(String name, Class<?> clazz) {
        validateName(name, "@UMLClass on " + clazz.getName());
    }

    public static void validateName(String name, String context) {
        checkArgument(!PlantUmlEscaper.label(name).isBlank(),
            "%s: name() must show something: it cannot be empty, nor made of invisible characters "
                + "or whitespace only.",
            context);
    }

    static void validateStereotype(String stereotype, Class<?> clazz) {
        validateStereotype(stereotype, "@UMLClass on " + clazz.getName());
    }

    public static void validateStereotype(String stereotype, String context) {
        if (stereotype.isEmpty()) {
            return;
        }
        checkArgument(!PlantUmlEscaper.stereotype(stereotype).isBlank(),
            "%s: stereotype() must show something: it cannot be made of invisible characters or "
                + "whitespace only.",
            context);
    }

    static void validateColor(String color, Class<?> clazz) {
        validateColor(color, "@UMLClass on " + clazz.getName());
    }

    static void validateColor(String color, String context) {
        if (color.isEmpty()) {
            return;
        }

        ColorCodec.code(color, context);
    }
}
