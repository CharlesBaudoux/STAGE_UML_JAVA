package io.github.oliviercailloux.umlg.annotation;

// Où placer la cible d'une association par rapport à sa source.
//
// PlantUML se sert de cette indication pour orienter son moteur de placement : "A -down-> B"
// pousse B sous A. C'est une contrainte de mise en page, sans aucun sens UML.
public enum UMLDirection {

    NONE,

    UP,
    DOWN,
    LEFT,
    RIGHT
}
