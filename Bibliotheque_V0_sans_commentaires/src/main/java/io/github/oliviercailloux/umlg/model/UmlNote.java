package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;

// Une bulle de commentaire, et l'élément auquel elle s'accroche.
//
// Les notes d'une entité sont tenues dans une seule liste, y compris celles qui visent un de ses
// membres : une note n'est pas un morceau de la boîte, c'est un objet posé à côté qui la
// désigne. Le point d'ancrage dit lequel des deux cas s'applique.
public record UmlNote(
    String text,
    UMLDirection direction,
    String anchor
) {

    public UmlNote {
        checkNotNull(text, "The note text cannot be null.");
        checkNotNull(direction, "The note direction cannot be null (use UMLDirection.NONE).");
        checkNotNull(anchor, "The note anchor cannot be null (use an empty string).");

        checkArgument(!text.isBlank(),
            "The note text cannot be empty or made of whitespace only.");
        checkArgument(anchor.isEmpty() || !anchor.isBlank(),
            "The note anchor cannot be made of whitespace only.");
    }

    public static UmlNote surClasse(String text, UMLDirection direction) {
        return new UmlNote(text, direction, "");
    }

    public boolean surMembre() {
        return !anchor.isEmpty();
    }
}
