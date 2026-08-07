// Le diagramme tel qu'il est dit, indépendamment de la façon dont il sera dessiné.
//
// Ce paquetage ne connaît ni la réflexion (paquetage extraction) ni PlantUML (paquetage
// rendering). Il décrit seulement ce qu'il y a à montrer : des entités, leurs membres, leurs
// relations.
//
// Où sont les gardes. Les invariants d'un élément appartiennent à l'élément : c'est lui qui doit
// rester sain quelle que soit la façon dont on le construit — par réflexion, par un test, par un
// appel direct. Ils sont donc écrits ici, dans les constructeurs compacts des records, sans
// aucune connaissance du code annoté ; l'extraction, qui la possède, se contente d'ajouter le
// « où » au « quoi » en enveloppant l'échec.
//
// Ce qui ne se valide pas ici. Les textes libres destinés à un lecteur humain — noms, rôles,
// labels — ne sont contraints qu'à désigner quelque chose : aucun caractère n'y est interdit.
// Leur sûreté syntaxique est l'affaire du rendu, seul à savoir dans quelle position syntaxique
// la valeur atterrit. À l'inverse, ce dont le vocabulaire est fermé — multiplicités, couleurs —
// se valide bien par liste blanche.
//
// Immuabilité. Tout y est record et ImmutableList : un modèle modifiable après construction ne
// pourrait pas garantir ses invariants.
package io.github.oliviercailloux.umlg.model;
