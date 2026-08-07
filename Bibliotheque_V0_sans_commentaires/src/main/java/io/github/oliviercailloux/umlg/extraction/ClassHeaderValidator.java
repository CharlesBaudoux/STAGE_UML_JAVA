package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.regex.Pattern;

// Vérifie ce que @UMLClass déclare : le nom, le stéréotype, la couleur.
//
// Ces contrôles vivent dans l'extraction, et non dans le modèle, parce que leurs messages
// nomment la classe fautive — une information que le modèle n'a pas.
final class ClassHeaderValidator {

    private static final Pattern COLOR_PATTERN =
        Pattern.compile("^#?([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$|^[A-Za-z][A-Za-z0-9]*$");

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
        if (color.isEmpty()) {
            return;
        }
        checkArgument(COLOR_PATTERN.matcher(color).matches(),
            "@UMLClass on %s: color() = \"%s\" is not a valid color (expected: 3 or 6 "
                + "hexadecimal digits with or without a leading '#', e.g. \"#FF0000\", "
                + "or a color name, e.g. \"LightBlue\").",
            clazz.getName(), color);
    }
}
