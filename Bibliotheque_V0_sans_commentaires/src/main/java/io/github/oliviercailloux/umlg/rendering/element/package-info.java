// Les morceaux de diagramme, un par forme dessinée.
//
// ClassBoxRenderer écrit une boîte, RelationRenderer écrit un trait. Chacun prend un objet du
// modèle et rend le fragment de texte PlantUML correspondant ; ni l'un ni l'autre n'écrit de
// fichier ni ne connaît le reste du diagramme.
//
// Une différence à noter : ClassBoxRenderer est une classe utilitaire statique, alors que
// RelationRenderer s'instancie — parce que lui accumule un état, la largeur de la plus grande
// étiquette rencontrée, dont dépend l'espacement final.
package io.github.oliviercailloux.umlg.rendering.element;
