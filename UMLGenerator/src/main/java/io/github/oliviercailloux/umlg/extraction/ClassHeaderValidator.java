package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

// Vérifie ce que @UMLClass déclare : le nom, le stéréotype, la couleur.
//
// Ces contrôles vivent dans l'extraction, et non dans le modèle, parce que leurs messages
// nomment la classe fautive — une information que le modèle n'a pas.
//
// LA QUESTION EST POSÉE AU TEXTE ÉCHAPPÉ
//
// Ce qui compte n'est pas ce que l'utilisateur a tapé, mais ce qui sera écrit dans le fichier
// PlantUML : c'est là que la chaîne peut être vide, et c'est vide qu'elle est fautive.
// isBlank() ne tient pas les caractères de contrôle pour des blancs, alors que l'échappement les
// supprime : un nom valant «  » traversait donc la validation et engendrait
// « class "" as C », que le moteur refuse — la génération s'arrêtait ensuite sur une
// IllegalStateException accusant « un défaut de la bibliothèque ». Poser la question après
// échappement la pose au bon texte, et couvre du même coup les blancs, qu'il conserve.
final class ClassHeaderValidator {

    private ClassHeaderValidator() {}

    // Le nom reçu ici est le nom final (défaut déjà résolu par EntityExtractor), il ne peut donc
    // être vide que si l'utilisateur a écrit name = "   ".
    static void validateName(String name, Class<?> clazz) {
        checkArgument(!PlantUmlEscaper.label(name).isBlank(),
            "@UMLClass on %s: name() cannot be empty, nor made of whitespace or invisible "
                + "characters only.",
            clazz.getName());
    }

    static void validateStereotype(String stereotype, Class<?> clazz) {
        if (stereotype.isEmpty()) {
            return;
        }
        checkArgument(!PlantUmlEscaper.stereotype(stereotype).isBlank(),
            "@UMLClass on %s: stereotype() cannot be made of whitespace or invisible characters "
                + "only.",
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
    //
    // La question est posée au catalogue de PlantUML plutôt qu'à une expression régulière. Une
    // grammaire écrite ici en doublon paraissait plus sobre — l'extraction n'a pas à connaître le
    // moteur de dessin —, mais elle divergeait : elle refusait « #FF000080 », que PlantUML
    // accepte, si bien que le processeur d'annotations laissait compiler du code qui ne pouvait
    // pas se dessiner. Une seule grammaire, celle du moteur, et l'écart n'existe plus.
    static void validateColor(String color, String contexte) {
        if (color.isEmpty()) {
            return;
        }
        // Appel fait pour son effet de bord : ColorCodec lève une IllegalArgumentException dont
        // le message nomme déjà le contexte et le catalogue.
        ColorCodec.code(color, contexte);
    }
}
