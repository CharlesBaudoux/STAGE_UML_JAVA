package io.github.oliviercailloux.umlg.model;

/**
 * Ce que toute relation a en commun : un point d'arrivée, et éventuellement un texte.
 *
 * <p>{@code sealed} : la liste des relations possibles est close. Deux bénéfices concrets —
 * personne ne peut ajouter une nature de relation depuis l'extérieur de la bibliothèque, et le
 * compilateur sait vérifier qu'un {@code switch} sur une {@code UmlRelation} traite bien tous les
 * cas. Ajouter une nature de relation oblige à toucher ce {@code permits}, donc à passer en revue
 * tous les endroits qui en dépendent — c'est l'effet recherché.
 *
 * <p>Les cinq natures ne se ressemblent pas toutes. Trois se lisent dans le code
 * ({@link UmlAssociation}, {@link UmlDependency}, {@link UmlInheritance}) ; la
 * {@link UmlAssociationClass} relie trois boîtes au lieu de deux ; le {@link UmlLayout}, lui, ne
 * se dessine pas du tout. Ce qu'elles partagent est ce qui figure ci-dessous, et c'est assez pour
 * qu'une seule liste les porte et qu'un seul renderer les écrive.
 */
public sealed interface UmlRelation
    permits UmlAssociation, UmlAssociationClass, UmlDependency, UmlInheritance, UmlLayout {

    /**
     * La cible, désignée par son nom complet plutôt que par une référence : le modèle se
     * construit ainsi entité par entité, sans avoir à résoudre les cibles au fur et à mesure.
     *
     * @return le nom complet de la boîte d'arrivée, jamais vide.
     */
    String targetQualifiedName();

    /**
     * Le texte écrit le long du trait.
     *
     * @return le texte posé sur le trait ; la chaîne vide s'il n'y en a pas.
     */
    String label();
}
