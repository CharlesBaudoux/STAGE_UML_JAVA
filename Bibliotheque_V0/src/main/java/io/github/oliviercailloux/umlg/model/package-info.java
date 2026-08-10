/**
 * Le diagramme tel qu'il est dit, indépendamment de la façon dont il sera dessiné.
 *
 * <p>Ce paquetage ne connaît ni la réflexion ni PlantUML. Il décrit seulement ce qu'il y a à
 * montrer : des entités ({@link io.github.oliviercailloux.umlg.model.UmlEntity}), leurs membres
 * ({@link io.github.oliviercailloux.umlg.model.UmlField},
 * {@link io.github.oliviercailloux.umlg.model.UmlMethod}), leurs relations
 * ({@link io.github.oliviercailloux.umlg.model.UmlRelation}), et ce qui vaut pour le dessin
 * entier ({@link io.github.oliviercailloux.umlg.model.UmlProject}).
 *
 * <p><b>Où sont les gardes.</b> Les invariants d'un élément appartiennent à l'élément : c'est lui
 * qui doit rester sain quelle que soit la façon dont on le construit — par réflexion, par un
 * test, par un appel direct. Ils sont donc écrits ici, dans les constructeurs compacts des
 * records, sans aucune connaissance du code annoté ; l'extraction, qui la possède, se contente
 * d'ajouter le « où » au « quoi » en enveloppant l'échec.
 *
 * <p><b>Ce qui ne se valide pas ici.</b> Les textes libres destinés à un lecteur humain — noms,
 * rôles, labels — ne sont contraints qu'à désigner quelque chose : aucun caractère n'y est
 * interdit. Leur sûreté syntaxique est l'affaire du rendu, seul à savoir dans quelle position
 * syntaxique la valeur atterrit. À l'inverse, ce dont le vocabulaire est fermé — multiplicités,
 * couleurs — se valide bien par liste blanche.
 *
 * <p><b>Immuabilité.</b> Tout y est {@code record} et {@code ImmutableList} : un modèle
 * modifiable après construction ne pourrait pas garantir ses invariants.
 */
package io.github.oliviercailloux.umlg.model;
