package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;

final class Strategie {

    private final Regles regles;

    Strategie(Regles regles) {
        this.regles = regles;
    }

    boolean connue(UMLClass classe) {
        return classe.strategy() != UMLVisibility.PROJECT || regles != null;
    }

    UMLVisibility effective(UMLClass classe) {
        if (classe.strategy() != UMLVisibility.PROJECT) {
            return classe.strategy();
        }
        return regles == null ? UMLVisibility.NONE : regles.strategie();
    }

    boolean champMasqueParUneRegle(String nom) {
        return regles != null && regles.membreMasque(nom);
    }
}
