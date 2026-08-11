package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;

/**
 * Une ligne du compartiment des attributs, par exemple {@code - solde : double {solde >= 0}}.
 *
 * @param name le nom affiché.
 * @param type nom simple du type ({@code String}, et non {@code java.lang.String}) : la boîte
 *     doit rester lisible.
 * @param cardinality la multiplicité de la propriété, écrite entre crochets à la suite du nom :
 *     {@code - Commande commandes [0..*]}. Vide = rien d'affiché, ce qui vaut « exactement un »
 *     en UML et couvre l'immense majorité des attributs. Elle n'est renseignée aujourd'hui que
 *     par une {@code @UMLAssociation} dessinée dans la boîte, seul cas où la bibliothèque
 *     connaît le nombre d'objets tenus.
 * @param visibility le symbole UML de visibilité, déjà traduit : {@code +} public, {@code -}
 *     private, {@code #} protected, {@code ~} package.
 * @param isStatic vrai pour un membre statique, que le diagramme souligne.
 * @param derived vrai pour un attribut qui n'est pas stocké mais calculé. UML le note d'une barre
 *     oblique devant son nom, que le rendu ajoute.
 * @param constraints les règles formelles attachées à l'attribut, sans leurs accolades : le rendu
 *     les pose.
 */
public record UmlField(
    String name,
    String type,
    String cardinality,
    String visibility,
    boolean isStatic,
    boolean derived,
    ImmutableList<String> constraints
) {

    /**
     * Vérifie les invariants de l'attribut.
     *
     * @throws NullPointerException si la multiplicité est nulle ; son absence se dit avec la
     *     chaîne vide.
     * @throws IllegalArgumentException si la multiplicité n'est pas une multiplicité UML, ou si
     *     une contrainte est mal formée.
     */
    public UmlField {
        checkNotNull(cardinality, "The cardinality cannot be null (use an empty string).");
        UmlAssociation.validateCardinality(cardinality, "cardinality");
        constraints = Contraintes.validees(constraints);
    }

    /**
     * Le cas ordinaire : un attribut stocké, d'un seul objet, sans contrainte.
     *
     * <p>Deux constructeurs, et deux seulement : celui-ci pour le cas courant, le canonique pour
     * tout le reste. Un troisième, intermédiaire, ne ferait qu'aligner des booléens anonymes que
     * personne ne saurait relire.
     *
     * @param name le nom affiché.
     * @param type le nom simple du type.
     * @param visibility le symbole UML de visibilité.
     * @param isStatic vrai pour un membre statique.
     */
    public UmlField(String name, String type, String visibility, boolean isStatic) {
        this(name, type, "", visibility, isStatic, false, ImmutableList.of());
    }
}
