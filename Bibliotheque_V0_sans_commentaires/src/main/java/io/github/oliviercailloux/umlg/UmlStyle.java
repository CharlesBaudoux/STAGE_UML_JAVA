package io.github.oliviercailloux.umlg;

// L'apparence du diagramme : qui, de PlantUML ou de la bibliothèque, décide des couleurs.
//
// Cette énumération ne change jamais ce que le diagramme dit — les mêmes boîtes, les mêmes
// traits, les mêmes membres. Elle ne porte que sur son habillage.
//
// POURQUOI ELLE EXISTE
//
// La bibliothèque imposait autrefois son thème à tout le monde, sans qu'aucun réglage ne
// permette de s'en écarter. Or l'apparence fait partie du diagramme que l'utilisateur veut :
// lui refuser ce choix, c'est décider à sa place. Les deux valeurs ci-dessous sont donc offertes
// à égalité, et la première est le défaut parce qu'elle est la moins présomptueuse — ne rien
// imposer est un meilleur point de départ qu'imposer nos goûts.
//
// CE QUI RESTE COMMUN AUX DEUX
//
// Trois réglages échappent au choix, parce qu'ils touchent au sens et non au goût :
//   - classAttributeIconSize 0 : les visibilités s'écrivent « + », « - », « # », « ~ », la
//     notation d'UML, plutôt qu'en pastilles colorées propres à PlantUML ;
//   - hide empty members : sans lui, une boîte sans membre — le cas courant sous
//     strategy = NONE — s'afficherait avec des compartiments vides ;
//   - l'espacement, calculé sur la plus longue étiquette du diagramme (voir PlantUmlLayout),
//     sans quoi les étiquettes de lien se chevauchent.
public enum UmlStyle {

    PLANTUML,

    UMLG
}
