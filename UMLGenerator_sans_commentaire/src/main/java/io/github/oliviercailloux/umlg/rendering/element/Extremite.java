package io.github.oliviercailloux.umlg.rendering.element;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

final class Extremite {

    private Extremite() {}

    static String source(UmlAssociation association) {
        ImmutableList.Builder<String> parties = ImmutableList.builder();

        ajouter(parties, association.sourceRole(), "%s");
        ajouter(parties, association.sourceCardinality(), "%s");

        return assembler(parties.build());
    }

    static String cible(UmlAssociation association) {
        ImmutableList.Builder<String> parties = ImmutableList.builder();

        ajouter(parties, association.targetRole(), association.derived() ? "/%s" : "%s");
        ajouter(parties, association.targetCardinality(), "%s");

        for (String regle : association.constraints()) {
            parties.add("{" + PlantUmlEscaper.label(regle) + "}");
        }
        return assembler(parties.build());
    }

    private static void ajouter(ImmutableList.Builder<String> parties, String texte,
        String gabarit) {
        if (texte.isEmpty()) {
            return;
        }
        parties.add(gabarit.formatted(PlantUmlEscaper.label(texte)));
    }

    private static String assembler(ImmutableList<String> parties) {
        return String.join(" ", parties);
    }
}
