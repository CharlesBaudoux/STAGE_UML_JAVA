package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * La règle commune aux trois porteurs de contraintes : une classe, un attribut, un lien.
 *
 * <p>Elle est écrite ici une seule fois parce qu'elle est la même partout — une contrainte dit
 * quelque chose, ou n'a pas lieu d'être. La recopier dans les trois records l'exposerait à
 * diverger au premier changement.
 *
 * <p><b>Pourquoi cette classe est publique</b>, comme {@link Multiplicites} et pour la même
 * raison. Elle ne l'était pas : c'était un rouage interne du paquetage, et rien de plus. Mais deux
 * moitiés de la bibliothèque doivent s'accorder sur cette règle — le modèle, qui refuse une
 * contrainte vide à la construction, et le processeur d'annotations, qui doit refuser la même à la
 * compilation. Le processeur vit dans un autre paquetage ; ne pouvant pas atteindre la règle, il
 * l'avait réécrite, et les deux textes avaient déjà divergé : {@code @UMLConstraint(rule = " ")}
 * s'entendait reprocher deux choses différentes selon le moment où la faute était vue.
 *
 * <p>La règle est donc atteignable, et n'existe qu'ici.
 */
public final class Contraintes {

    private Contraintes() {}

    /**
     * Vérifie qu'une contrainte dit quelque chose.
     *
     * @param regle la contrainte à vérifier, telle que l'utilisateur l'a écrite.
     * @throws IllegalArgumentException si elle est vide ou faite d'espaces.
     */
    public static void validate(String regle) {
        checkArgument(!checkNotNull(regle, "A constraint cannot be null.").isBlank(),
            "A constraint cannot be empty or made of whitespace only.");
    }

    static ImmutableList<String> validees(List<String> regles) {
        ImmutableList<String> figees = ImmutableList.copyOf(
            checkNotNull(regles, "The list of constraints cannot be null."));

        for (String regle : figees) {
            validate(regle);
        }
        return figees;
    }
}
