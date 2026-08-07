package io.github.oliviercailloux.umlg.model;

// Ce que toute relation a en commun : un point d'arrivée, et éventuellement un texte.
//
// sealed : la liste des relations possibles est close. Deux bénéfices concrets — personne ne
// peut ajouter une nature de relation depuis l'extérieur de la bibliothèque, et le compilateur
// sait vérifier qu'un switch sur une UmlRelation traite bien les deux cas (voir
// RelationRenderer). Ajouter une nature de relation obligerait à toucher ce permits, donc à
// passer en revue tous les endroits qui en dépendent.
public sealed interface UmlRelation permits UmlAssociation, UmlDependency, UmlInheritance {

    String targetQualifiedName();

    String label();
}
