package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;

/**
 * Une bulle de commentaire, et l'élément auquel elle s'accroche.
 *
 * <p>Les notes d'une entité sont tenues dans une seule liste, y compris celles qui visent un de
 * ses membres : une note n'est pas un morceau de la boîte, c'est un objet posé à côté qui la
 * désigne. Le point d'ancrage dit lequel des deux cas s'applique.
 *
 * @param text le texte affiché. Ni vide, ni fait d'espaces : une note doit dire quelque chose.
 * @param direction où poser la bulle. {@code NONE} laisse le rendu choisir.
 * @param anchor le nom du membre visé, ou la chaîne vide pour une note attachée à la classe
 *     entière.
 */
public record UmlNote(
    String text,
    UMLDirection direction,
    String anchor
) {

    /**
     * Vérifie les invariants de la note.
     *
     * @throws NullPointerException si l'une des composantes est nulle ; l'absence d'ancre se dit
     *     avec la chaîne vide.
     * @throws IllegalArgumentException si le texte est vide, ou si le texte ou l'ancre n'est fait
     *     que d'espaces.
     */
    public UmlNote {
        checkNotNull(text, "The note text cannot be null.");
        checkNotNull(direction, "The note direction cannot be null (use UMLDirection.NONE).");
        checkNotNull(anchor, "The note anchor cannot be null (use an empty string).");

        checkArgument(!text.isBlank(),
            "The note text cannot be empty or made of whitespace only.");
        checkArgument(anchor.isEmpty() || !anchor.isBlank(),
            "The note anchor cannot be made of whitespace only.");
    }

    /**
     * Note attachée à la classe entière : le cas le plus courant, et celui où l'ancre n'a rien à
     * désigner.
     *
     * @param text le texte affiché.
     * @param direction où poser la bulle.
     * @return la note, sans ancre.
     */
    public static UmlNote onClass(String text, UMLDirection direction) {
        return new UmlNote(text, direction, "");
    }

    /**
     * La note vise-t-elle un membre plutôt que la classe entière ?
     *
     * @return vrai si la note vise un membre précis, faux si elle vise la classe entière.
     */
    public boolean isOnMember() {
        return !anchor.isEmpty();
    }
}
