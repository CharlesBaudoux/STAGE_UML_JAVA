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

    // Rend la liste sous sa forme immuable, après l'avoir vérifiée.
    //
    // Les deux gestes vont ensemble : valider sans figer laisserait l'appelant modifier la liste
    // après coup, et figer sans valider n'empêcherait qu'une moitié des dégâts.
    static ImmutableList<String> validees(List<String> regles) {
        // copyOf refuse au passage les éléments nuls : une règle absente n'a pas à être
        // vérifiée ici, la collection immuable de Guava s'en charge déjà.
        ImmutableList<String> figees = ImmutableList.copyOf(
            checkNotNull(regles, "The list of constraints cannot be null."));

        for (String regle : figees) {
            checkArgument(!regle.isBlank(),
                "A constraint cannot be empty or made of whitespace only.");
        }
        return figees;
    }
}
