package io.github.oliviercailloux.umlg.rendering.element;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.model.UmlNote;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

public final class NoteRenderer {

    private NoteRenderer() {}

    public static String note(UmlNote note, String alias) {
        if (!note.isOnMember()) {
            return "note " + placement(note.direction()) + " of " + alias + " : "
                + PlantUmlEscaper.debutDeLigne(note.text()) + "\n";
        }

        return "note " + placementSurMembre(note.direction()) + " of " + alias + "::"
            + PlantUmlEscaper.label(note.anchor()) + "\n"
            + PlantUmlEscaper.noteBloc(note.text()) + "\n"
            + "end note\n";
    }

    public static String contrainte(String regle, String alias) {
        return "note " + placement(UMLDirection.NONE) + " of " + alias + " : "
            + NotationContrainte.une(regle) + "\n";
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
