package io.github.oliviercailloux.umlg.annotation;

/**
 * Où placer la cible d'une association par rapport à sa source.
 *
 * <p>PlantUML se sert de cette indication pour orienter son moteur de placement : "A -down-&gt; B"
 * pousse B sous A. C'est une contrainte de mise en page, sans aucun sens UML.
 */
public enum UMLDirection {

    // Aucune contrainte : PlantUML choisit seul. C'est le défaut, et le bon choix en général.
    NONE,

    UP,
    DOWN,
    LEFT,
    RIGHT
}
