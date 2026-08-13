package io.github.oliviercailloux.umlg.rendering.element;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

final class Extremite {

    record Etiquette(String ecrit, String affiche) {

        boolean estVide() {
            return ecrit.isEmpty();
        }
    }

    private Extremite() {}

    static Etiquette source(UmlAssociation association) {
        Morceaux morceaux = new Morceaux();

        morceaux.ajouter(association.sourceRole(), "%s");
        morceaux.ajouter(association.sourceMultiplicity(), "%s");

        return morceaux.assembler();
    }

    static Etiquette cible(UmlAssociation association) {
        Morceaux morceaux = new Morceaux();

        morceaux.ajouter(association.targetRole(), association.derived() ? "/%s" : "%s");
        morceaux.ajouter(association.targetMultiplicity(), "%s");

        for (String regle : association.constraints()) {
            morceaux.ajouterTelQuel(NotationContrainte.une(regle), "{" + regle + "}");
        }
        return morceaux.assembler();
    }

    private static final class Morceaux {

        private final ImmutableList.Builder<String> ecrits = ImmutableList.builder();
        private final ImmutableList.Builder<String> affiches = ImmutableList.builder();

        void ajouter(String texte, String gabarit) {
            if (texte.isEmpty()) {
                return;
            }
            ajouterTelQuel(gabarit.formatted(PlantUmlEscaper.label(texte)),
                gabarit.formatted(texte));
        }

        void ajouterTelQuel(String ecrit, String affiche) {
            ecrits.add(ecrit);
            affiches.add(affiche);
        }

        Etiquette assembler() {
            return new Etiquette(String.join(" ", ecrits.build()),
                String.join(" ", affiches.build()));
        }
    }
}
