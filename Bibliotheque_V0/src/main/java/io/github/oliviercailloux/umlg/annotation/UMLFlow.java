package io.github.oliviercailloux.umlg.annotation;

// Le sens dans lequel le diagramme entier se déroule.
//
// À ne pas confondre avec UMLDirection, qui place une boîte par rapport à une autre : celui-ci
// ne vise aucun élément en particulier, il dit dans quelle direction le moteur de rendu déplie
// l'ensemble du dessin. C'est pourquoi il ne se déclare qu'une fois, sur @UMLDiagram.
public enum UMLFlow {

    // Le défaut de PlantUML, et le nôtre : les héritages descendent, le diagramme s'allonge vers
    // le bas. C'est le sens de lecture d'un diagramme de classes ordinaire.
    TOP_TO_BOTTOM,

    // Le diagramme se déplie vers la droite. Utile quand les boîtes sont nombreuses et peu
    // profondes : un dessin large se lit alors mieux qu'un dessin haut.
    LEFT_TO_RIGHT
}
