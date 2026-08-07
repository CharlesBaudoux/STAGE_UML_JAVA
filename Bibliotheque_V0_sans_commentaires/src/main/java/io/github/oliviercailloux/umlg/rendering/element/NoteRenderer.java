package io.github.oliviercailloux.umlg.rendering.element;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.model.UmlNote;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

// Écrit les bulles de commentaire.
//
// Forme produite, selon ce que la note vise :
//   note right of Client : Ne pas modifier sans relire le contrat
//   note left of Client::valider
//   &#65;lgorithme critique
//   end note
//
// Deux formes, parce que PlantUML n'en accepte qu'une dans chaque cas : la forme d'une seule
// ligne lui suffit pour une classe, mais il la refuse pour un membre, où seul le bloc fonctionne.
// Le fait a été vérifié plutôt que supposé.
//
// Les côtés disponibles diffèrent eux aussi, et pour la même raison — l'un a été vérifié, l'autre
// aussi : une note de classe se pose des quatre côtés, une note de membre à gauche ou à droite
// seulement. D'où deux traductions du même énuméré, placement() et placementSurMembre().
//
// Chacune porte donc la protection que sa position exige. La forme d'une seule ligne n'a rien à
// refermer : l'échappement, qui interdit tout retour à la ligne dans le texte, suffit à garantir
// que rien n'en sortira. Le bloc, lui, se termine par une ligne « end note » qu'un texte pourrait
// imiter — d'où PlantUmlEscaper.noteBloc, qui rend cette imitation impossible.
public final class NoteRenderer {

    private NoteRenderer() {}

    public static String note(UmlNote note, String alias) {
        if (!note.surMembre()) {
            return "note " + placement(note.direction()) + " of " + alias + " : "
                + PlantUmlEscaper.label(note.text()) + "\n";
        }

        return "note " + placementSurMembre(note.direction()) + " of " + alias + "::"
            + PlantUmlEscaper.label(note.anchor()) + "\n"
            + PlantUmlEscaper.noteBloc(note.text()) + "\n"
            + "end note\n";
    }

    public static String contrainte(String regle, String alias) {
        return "note " + placement(UMLDirection.NONE) + " of " + alias + " : {"
            + PlantUmlEscaper.label(regle) + "}\n";
    }

    private static String placement(UMLDirection direction) {
        return switch (direction) {
            case NONE, RIGHT -> "right";
            case LEFT -> "left";
            case UP -> "top";
            case DOWN -> "bottom";
        };
    }

    private static String placementSurMembre(UMLDirection direction) {
        return direction == UMLDirection.LEFT ? "left" : "right";
    }
}
