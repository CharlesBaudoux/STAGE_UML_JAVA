package io.github.oliviercailloux.umlg.model;

// Ce que toute relation a en commun : un point d'arrivée, et éventuellement un texte.
//
// sealed : la liste des relations possibles est close. Deux bénéfices concrets — personne ne
// peut ajouter une nature de relation depuis l'extérieur de la bibliothèque, et le compilateur
// sait vérifier qu'un switch sur une UmlRelation traite bien tous les cas (voir
// RelationRenderer). Ajouter une nature de relation oblige à toucher ce permits, donc à passer
// en revue tous les endroits qui en dépendent — c'est l'effet recherché.
//
// Les cinq natures ne se ressemblent pas toutes. Trois se lisent dans le code (association,
// dépendance, héritage) ; la classe d'association relie trois boîtes au lieu de deux ; le
// placement, lui, ne se dessine pas du tout. Ce qu'elles partagent est ce qui figure ci-dessous,
// et c'est assez pour qu'une seule liste les porte et qu'un seul renderer les écrive.
public sealed interface UmlRelation
    permits UmlAssociation, UmlAssociationClass, UmlDependency, UmlInheritance, UmlLayout {

    // La cible, désignée par son nom complet plutôt que par une référence : le modèle se
    // construit ainsi entité par entité, sans avoir à résoudre les cibles au fur et à mesure.
    String targetQualifiedName();

    // Texte posé sur le trait ; chaîne vide s'il n'y en a pas.
    String label();
}
