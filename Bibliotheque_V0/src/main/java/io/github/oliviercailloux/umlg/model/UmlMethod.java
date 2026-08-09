package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;

// Une ligne du compartiment des méthodes, par exemple "+ retirer(montant: double): void".
public record UmlMethod(
    String name,
    // Nom simple du type de retour, "void" compris.
    //
    // La chaîne vide y est admise, et veut dire « ne rien écrire après les parenthèses ». UML
    // tient le type de retour pour facultatif, et beaucoup de diagrammes tenus à la main taisent
    // le void, qui n'apprend rien. C'est une règle du projet qui en décide (voir Regles) ; par
    // défaut le type est écrit, void compris.
    String returnType,
    // Combien d'objets la méthode rend, écrit entre crochets après le type de retour :
    // « names(): String [0..*] ». Vide = rien d'affiché, ce qui vaut « exactement un ».
    // Renseignée par @UMLMultiplicity, et validée par la même grammaire que celle des liens.
    String cardinality,
    // Symbole UML de visibilité, voir UmlField.
    String visibility,
    boolean isStatic,
    // Les paramètres, dans l'ordre de la signature. Chacun porte son type et, quand la
    // compilation l'a conservé, son nom — voir UmlParameter.
    ImmutableList<UmlParameter> parameters,
    // Une méthode dérivée rend une valeur calculée plutôt que stockée. Même notation que pour un
    // attribut : une barre oblique devant le nom.
    boolean derived,
    // Une opération sans corps : abstraite au sens de Java, ou déclarée par une interface.
    //
    // UML l'écrit en italique, et c'est la seule chose qui distingue à l'œil un contrat d'une
    // implémentation. La composante manquait : dans une classe abstraite ou une interface,
    // toutes les méthodes se dessinaient comme si elles étaient écrites.
    boolean isAbstract
) {
    public UmlMethod {
        checkNotNull(returnType, "The return type cannot be null (use an empty string).");
        checkNotNull(cardinality, "The cardinality cannot be null (use an empty string).");
        UmlAssociation.validateCardinality(cardinality, "cardinality");

        // Recopie défensive : la liste passée par l'appelant reste modifiable de son côté, celle
        // du record ne l'est pas. copyOf refuse au passage les éléments nuls.
        parameters = ImmutableList.copyOf(
            checkNotNull(parameters, "The list of parameters cannot be null."));
    }

    // Une méthode concrète, qui est le cas ordinaire : l'abstraction est l'exception, et une
    // classe sur laquelle on ne dit rien est une classe dont les méthodes ont un corps.
    public UmlMethod(String name, String returnType, String cardinality, String visibility,
        boolean isStatic, ImmutableList<UmlParameter> parameters, boolean derived) {
        this(name, returnType, cardinality, visibility, isStatic, parameters, derived, false);
    }

    // Le cas ordinaire : une méthode non dérivée, dont on ne connaît que les types des
    // paramètres. Même raison que pour UmlField — deux booléens à la file ne se lisent plus.
    //
    // Il accepte une List quelconque là où le constructeur canonique exige une ImmutableList :
    // c'est le point d'entrée commode, celui qui laisse l'appelant écrire List.of(...) sans rien
    // savoir de Guava. La recopie a lieu ici, et la composante reste immuable.
    public UmlMethod(String name, String returnType, String visibility, boolean isStatic,
        List<String> parameterTypes) {
        this(name, returnType, "", visibility, isStatic,
            checkNotNull(parameterTypes, "The list of parameter types cannot be null.").stream()
                .map(UmlParameter::anonyme)
                .collect(ImmutableList.toImmutableList()),
            false, false);
    }
}
