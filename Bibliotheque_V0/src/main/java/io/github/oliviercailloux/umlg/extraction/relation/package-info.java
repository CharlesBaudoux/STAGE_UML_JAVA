// Ce qui relie les boîtes entre elles.
//
// Un extracteur par nature de relation (AssociationExtractor, DependencyExtractor), tous deux
// derrière la même interface RelationExtractor : EntityExtractor les appelle en boucle sans
// savoir ce qu'ils cherchent.
//
// Deux services partagés : TargetResolver, qui vérifie que la cible déclarée fait bien partie
// du diagramme, et Emplacement, qui donne aux messages d'erreur une forme uniforme.
package io.github.oliviercailloux.umlg.extraction.relation;
