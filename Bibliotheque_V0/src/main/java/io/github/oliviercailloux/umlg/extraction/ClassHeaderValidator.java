package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.regex.Pattern;

// Vérifie ce que @UMLClass déclare : le nom, le stéréotype, la couleur.
//
// Ces contrôles vivent dans l'extraction, et non dans le modèle, parce que leurs messages
// nomment la classe fautive — une information que le modèle n'a pas.
final class ClassHeaderValidator {

    // Une couleur, contrairement à un nom, n'est pas un texte libre : c'est soit un code
    // hexadécimal à 3 ou 6 chiffres (« AABBCC », « #FF0000», « #ABC »), soit un nom de
    // couleur (« red », « LightBlue »). Cette vérification reste purement syntaxique et
    // indépendante du moteur de rendu ; savoir si « Turquoise » existe vraiment relève du
    // writer, qui interroge le catalogue de PlantUML.
    private static final Pattern COLOR_PATTERN =
        Pattern.compile("^#?([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$|^[A-Za-z][A-Za-z0-9]*$");

    private ClassHeaderValidator() {}

    // Le nom reçu ici est le nom final (défaut déjà résolu par EntityExtractor), il ne peut donc
    // être vide que si l'utilisateur a écrit name = "   ".
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

    // La même règle, sous un autre en-tête de message.
    //
    // Une couleur de groupe s'écrit dans @UMLPackage, et se vérifie exactement comme celle d'une
    // boîte : c'est la même grammaire, et il n'y aurait aucun sens à ce qu'un rectangle accepte
    // ce qu'une boîte refuse. Seul le contexte change, et il change parce que le message doit
    // nommer l'annotation que l'utilisateur a réellement écrite.
    static void validateColor(String color, String contexte) {
        if (color.isEmpty()) {
            return;
        }
        checkArgument(COLOR_PATTERN.matcher(color).matches(),
            "%s: color() = \"%s\" is not a valid color (expected: 3 or 6 "
                + "hexadecimal digits with or without a leading '#', e.g. \"#FF0000\", "
                + "or a color name, e.g. \"LightBlue\").",
            contexte, color);
    }
}
