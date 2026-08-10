package io.github.oliviercailloux.umlg.annotation;

/**
 * Où placer la cible d'une association par rapport à sa source.
 *
 * <p>PlantUML se sert de cette indication pour orienter son moteur de placement : "A -down-&gt; B"
 * pousse B sous A. C'est une contrainte de mise en page, sans aucun sens UML.
 */
public enum UMLDirection {

    /**
     * Aucune contrainte : PlantUML choisit seul. C'est le défaut, et le bon choix en général.
     */
    NONE,

    /** La cible est poussée au-dessus de la source. */
    UP,

    /** La cible est poussée en dessous de la source. */
    DOWN,

    /** La cible est poussée à gauche de la source. */
    LEFT,

    /** La cible est poussée à droite de la source. */
    RIGHT
}
