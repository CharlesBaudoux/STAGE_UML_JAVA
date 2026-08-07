package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;

// La règle commune aux trois porteurs de contraintes : une classe, un attribut, un lien.
//
// Elle est écrite ici une seule fois parce qu'elle est la même partout — une contrainte dit
// quelque chose, ou n'a pas lieu d'être. La recopier dans les trois records l'exposerait à
// diverger au premier changement.
//
// Package-private : le paquetage model est exporté, mais cette classe ne l'est pas pour autant.
// C'est un rouage interne, pas une promesse faite à l'utilisateur.
final class Contraintes {

    private Contraintes() {}

    static ImmutableList<String> validees(List<String> regles) {
        ImmutableList<String> figees = ImmutableList.copyOf(
            checkNotNull(regles, "The list of constraints cannot be null."));

        for (String regle : figees) {
            checkArgument(!regle.isBlank(),
                "A constraint cannot be empty or made of whitespace only.");
        }
        return figees;
    }
}
