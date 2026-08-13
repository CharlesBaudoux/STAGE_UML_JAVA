package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.VisibilityPolicy;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import javax.lang.model.element.Element;

final class Strategie {

    enum Affichage {
        AFFICHE,
        MASQUE,
        INDECIDABLE
    }

    private final Regles regles;

    Strategie(Regles regles) {
        this.regles = regles;
    }

    private boolean connue(UMLClass classe) {
        return classe.strategy() != UMLVisibility.PROJECT || regles != null;
    }

    boolean reglesConnues() {
        return regles != null;
    }

    Affichage membreAffiche(Element membre, UMLClass classe) {
        return decider(membre, classe,
            membre.getAnnotation(UMLInclude.class) != null,
            membre.getAnnotation(UMLExclude.class) != null);
    }

    Affichage membreAfficheSansDerogation(Element membre, UMLClass classe) {
        return decider(membre, classe, false, false);
    }

    private Affichage decider(Element membre, UMLClass classe, boolean inclusion,
        boolean exclusion) {
        if (!connue(classe)) {
            return Affichage.INDECIDABLE;
        }
        if (Membre.vientDUnComposant(membre)) {
            return Affichage.AFFICHE;
        }
        return VisibilityPolicy.estVisible(Membre.bits(membre), effective(classe), inclusion,
            exclusion) ? Affichage.AFFICHE : Affichage.MASQUE;
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
