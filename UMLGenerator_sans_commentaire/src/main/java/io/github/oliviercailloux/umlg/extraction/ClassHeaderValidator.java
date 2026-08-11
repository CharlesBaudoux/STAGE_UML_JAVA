package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;

final class ClassHeaderValidator {

    private ClassHeaderValidator() {}

    static void validateName(String name, Class<?> clazz) {
        checkArgument(!name.isBlank(),
            "@UMLClass on %s: name() cannot be empty or made of whitespace only.",
            clazz.getName());
    }

    static void validateStereotype(String stereotype, Class<?> clazz) {
        if (stereotype.isEmpty()) {
            return;
        }
        checkArgument(!stereotype.isBlank(),
            "@UMLClass on %s: stereotype() cannot be made of whitespace only.",
            clazz.getName());
    }

    static void validateColor(String color, Class<?> clazz) {
        validateColor(color, "@UMLClass on " + clazz.getName());
    }

    static void validateColor(String color, String contexte) {
        if (color.isEmpty()) {
            return;
        }
        ColorCodec.code(color, contexte);
    }
}
