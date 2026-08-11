package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
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

    // Les symboles de visibilité d'UML, et la chaîne vide pour « n'en écrire aucun ».
    private static final ImmutableList<String> VISIBILITES =
        ImmutableList.of("+", "-", "#", "~", "");

    /**
     * Vérifie les invariants de l'attribut.
     *
     * <p>Le nom, le type et la visibilité étaient les trois seules composantes de tout le modèle à
     * n'être jamais contrôlées : {@code new UmlField(null, null, null, false)} passait, et le rendu
     * écrivait {@code - null : null} dans la boîte, deux couches plus loin et sans un mot sur
     * l'origine. Le voisin immédiat {@link UmlParameter} refusait déjà un type vide.
     *
     * @throws NullPointerException si le nom, le type, la multiplicité ou la visibilité est nul ;
     *     l'absence de multiplicité se dit avec la chaîne vide, et celle de visibilité aussi.
     * @throws IllegalArgumentException si le nom ou le type est vide, si la visibilité n'est pas un
     *     symbole UML, si la multiplicité n'est pas une multiplicité UML, ou si une contrainte est
     *     mal formée.
     */
    public UmlField {
        verifierNom(name);
        checkNotNull(type, "The field type cannot be null.");
        checkArgument(!type.isBlank(), "The type of field \"%s\" cannot be empty.", name);
        checkNotNull(cardinality, "The cardinality cannot be null (use an empty string).");
        Multiplicites.valider(cardinality, "cardinality");
        verifierVisibilite(visibility, name);
        constraints = Contraintes.validees(constraints);
    }

    // Les deux invariants que les deux compartiments partagent, écrits ici et appelés par
    // UmlMethod : c'est la même règle, et une règle écrite deux fois finit toujours par diverger.
    // UmlMethod dit d'ailleurs déjà de sa visibilité qu'elle est « comme pour UmlField ».

    // Un membre sans nom ne désigne rien : il n'y a pas d'absence légitime à dire ici, à la
    // différence du nom d'un paramètre, que la compilation peut n'avoir pas conservé.
    static void verifierNom(String name) {
        checkNotNull(name, "The member name cannot be null.");
        checkArgument(!name.isBlank(), "The member name cannot be empty.");
    }

    // La chaîne vide est admise, et ne veut pas dire « on ne sait pas » : c'est ce que produit la
    // règle « omit visibility » du projet, qui demande de ne rien écrire devant le membre. Le
    // reste est un vocabulaire fermé — les quatre symboles d'UML —, donc validé par liste
    // blanche, comme les multiplicités et les couleurs.
    static void verifierVisibilite(String visibility, String name) {
        checkNotNull(visibility, "The visibility cannot be null (use an empty string).");
        checkArgument(VISIBILITES.contains(visibility),
            "The visibility of \"%s\" must be one of + - # ~, or empty to write none (got: \"%s\").",
            name, visibility);
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
