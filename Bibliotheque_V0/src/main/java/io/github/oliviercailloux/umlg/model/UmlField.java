package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;

// Une ligne du compartiment des attributs, par exemple "- solde : double {solde >= 0}".
public record UmlField(
    String name,
    // Nom simple du type (String, et non java.lang.String) : la boîte doit rester lisible.
    String type,
    // La multiplicité de la propriété, écrite entre crochets à la suite du nom :
    // « - Commande commandes [0..*] ». Vide = rien d'affiché, ce qui vaut « exactement un » en
    // UML et couvre l'immense majorité des attributs.
    //
    // Elle n'est renseignée aujourd'hui que par une @UMLAssociation dessinée dans la boîte
    // (UMLNotation.ATTRIBUTE), seul cas où la bibliothèque connaît le nombre d'objets tenus. La
    // grammaire est celle des multiplicités de lien, validée par la même règle : il n'y en a
    // qu'une en UML, qu'elle se lise le long d'un trait ou à côté d'un nom.
    String cardinality,
    // Le symbole UML de visibilité, déjà traduit par VisibilityPolicy.symbole :
    // "+" public, "-" private, "#" protected, "~" package.
    String visibility,
    // Un membre statique se souligne dans le diagramme.
    boolean isStatic,
    // Un attribut dérivé n'est pas stocké : il se calcule. UML le note d'une barre oblique
    // devant son nom, que le rendu ajoute.
    boolean derived,
    // Les règles formelles attachées à l'attribut, sans leurs accolades : le rendu les pose.
    ImmutableList<String> constraints
) {

    public UmlField {
        checkNotNull(cardinality, "The cardinality cannot be null (use an empty string).");
        UmlAssociation.validateCardinality(cardinality, "cardinality");
        constraints = Contraintes.validees(constraints);
    }

    // Le cas ordinaire : un attribut stocké, d'un seul objet, sans contrainte.
    //
    // Deux constructeurs, et deux seulement : celui-ci pour le cas courant, le canonique pour
    // tout le reste. Un troisième, intermédiaire, ne ferait qu'aligner des booléens anonymes que
    // personne ne saurait relire — « new UmlField("solde", "double", "-", false, false) » ne dit
    // pas lequel des deux est lequel.
    public UmlField(String name, String type, String visibility, boolean isStatic) {
        this(name, type, "", visibility, isStatic, false, ImmutableList.of());
    }
}
