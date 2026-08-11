package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Une ligne du compartiment des méthodes, par exemple {@code + retirer(montant: double): void}.
 *
 * @param name le nom affiché.
 * @param returnType nom simple du type de retour, {@code void} compris. La chaîne vide y est
 *     admise, et veut dire « ne rien écrire après les parenthèses » : UML tient le type de retour
 *     pour facultatif, et beaucoup de diagrammes tenus à la main taisent le {@code void}, qui
 *     n'apprend rien. C'est une règle du projet qui en décide ; par défaut le type est écrit.
 * @param cardinality combien d'objets la méthode rend, écrit entre crochets après le type de
 *     retour : {@code names(): String [0..*]}. Vide = rien d'affiché, ce qui vaut « exactement
 *     un ». Renseignée par {@code @UMLMultiplicity}.
 * @param visibility le symbole UML de visibilité, comme pour {@link UmlField}.
 * @param isStatic vrai pour une méthode statique, que le diagramme souligne.
 * @param parameters les paramètres, dans l'ordre de la signature. Chacun porte son type et, quand
 *     la compilation l'a conservé, son nom — voir {@link UmlParameter}.
 * @param derived vrai pour une méthode qui rend une valeur calculée plutôt que stockée. Même
 *     notation que pour un attribut : une barre oblique devant le nom.
 * @param isAbstract vrai pour une opération sans corps : abstraite au sens de Java, ou déclarée
 *     par une interface. UML l'écrit en italique, et c'est la seule chose qui distingue à l'œil
 *     un contrat d'une implémentation.
 */
public record UmlMethod(
    String name,
    String returnType,
    String cardinality,
    String visibility,
    boolean isStatic,
    ImmutableList<UmlParameter> parameters,
    boolean derived,
    boolean isAbstract
) {
    /**
     * Vérifie les invariants de la méthode.
     *
     * <p>Comme pour {@link UmlField}, le nom et la visibilité n'étaient pas contrôlés : une méthode
     * sans nom traversait le modèle et ne se voyait qu'à la lecture du dessin. Le type de retour,
     * lui, admet bien la chaîne vide — c'est ce qu'écrit la règle « omit return void » —, mais pas
     * un blanc, qui serait un oubli déguisé.
     *
     * @throws NullPointerException si le nom, le type de retour, la multiplicité, la visibilité ou
     *     la liste des paramètres est nulle ; l'absence de type de retour, de multiplicité et de
     *     visibilité se dit avec la chaîne vide.
     * @throws IllegalArgumentException si le nom est vide, si le type de retour n'est fait que
     *     d'espaces, si la visibilité n'est pas un symbole UML, ou si la multiplicité n'est pas une
     *     multiplicité UML.
     */
    public UmlMethod {
        UmlField.verifierNom(name);
        checkNotNull(returnType, "The return type cannot be null (use an empty string).");
        checkArgument(returnType.isEmpty() || !returnType.isBlank(),
            "The return type of \"%s\" cannot be made of whitespace only.", name);
        checkNotNull(cardinality, "The cardinality cannot be null (use an empty string).");
        Multiplicites.valider(cardinality, "cardinality");
        UmlField.verifierVisibilite(visibility, name);

        // Recopie défensive : la liste passée par l'appelant reste modifiable de son côté, celle
        // du record ne l'est pas. copyOf refuse au passage les éléments nuls.
        parameters = ImmutableList.copyOf(
            checkNotNull(parameters, "The list of parameters cannot be null."));
    }

    /**
     * Une méthode concrète, qui est le cas ordinaire : l'abstraction est l'exception, et une
     * classe sur laquelle on ne dit rien est une classe dont les méthodes ont un corps.
     *
     * @param name le nom affiché.
     * @param returnType le nom simple du type de retour.
     * @param cardinality la multiplicité du retour, ou la chaîne vide.
     * @param visibility le symbole UML de visibilité.
     * @param isStatic vrai pour une méthode statique.
     * @param parameters les paramètres, dans l'ordre de la signature.
     * @param derived vrai pour une méthode dérivée.
     */
    public UmlMethod(String name, String returnType, String cardinality, String visibility,
        boolean isStatic, ImmutableList<UmlParameter> parameters, boolean derived) {
        this(name, returnType, cardinality, visibility, isStatic, parameters, derived, false);
    }

    /**
     * Le cas ordinaire : une méthode non dérivée, dont on ne connaît que les types des
     * paramètres.
     *
     * <p>Il accepte une {@code List} quelconque là où le constructeur canonique exige une
     * {@code ImmutableList} : c'est le point d'entrée commode, celui qui laisse l'appelant écrire
     * {@code List.of(...)} sans rien savoir de Guava. La recopie a lieu ici, et la composante
     * reste immuable.
     *
     * @param name le nom affiché.
     * @param returnType le nom simple du type de retour.
     * @param visibility le symbole UML de visibilité.
     * @param isStatic vrai pour une méthode statique.
     * @param parameterTypes les types des paramètres, dans l'ordre de la signature ; leurs noms
     *     ne seront pas affichés.
     */
    public UmlMethod(String name, String returnType, String visibility, boolean isStatic,
        List<String> parameterTypes) {
        this(name, returnType, "", visibility, isStatic,
            checkNotNull(parameterTypes, "The list of parameter types cannot be null.").stream()
                .map(UmlParameter::anonyme)
                .collect(ImmutableList.toImmutableList()),
            false, false);
    }
}
