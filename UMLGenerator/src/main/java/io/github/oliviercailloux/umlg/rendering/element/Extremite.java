package io.github.oliviercailloux.umlg.rendering.element;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

// L'étiquette posée à un bout d'un trait, par exemple « achats 0..* {subset} ».
//
// Séparée de RelationRenderer parce que c'est un autre métier : celui-ci décide de la forme du
// trait — flèche, losange, pointillé —, celle-ci compose le texte de ses extrémités. Les deux
// changent pour des raisons différentes, et se lisent mieux séparément.
//
// Les deux bouts ne portent pas les mêmes morceaux, et c'est voulu : les contraintes ne se posent
// que du côté qui les subit.
final class Extremite {

    private Extremite() {}

    // Côté source : rôle, multiplicité — dans cet ordre, celui que lit l'œil de gauche à droite.
    static String source(UmlAssociation association) {
        ImmutableList.Builder<String> parties = ImmutableList.builder();

        ajouter(parties, association.sourceRole(), "%s");
        ajouter(parties, association.sourceCardinality(), "%s");

        return assembler(parties.build());
    }

    // Côté cible : rôle, multiplicité, puis les contraintes.
    static String cible(UmlAssociation association) {
        ImmutableList.Builder<String> parties = ImmutableList.builder();

        // La barre oblique d'un lien dérivé précède le rôle, qui est le nom de la propriété.
        ajouter(parties, association.targetRole(), association.derived() ? "/%s" : "%s");
        ajouter(parties, association.targetCardinality(), "%s");

        // Les contraintes s'écrivent toutes de la même façon, et c'est voulu : « {subset} » écrit
        // par l'auteur ne se distingue pas d'un « {ordered} » sur le dessin, et n'a aucune raison
        // de le faire. C'est ce qui a permis de retirer le paramètre isOrdered, qui produisait ce
        // même « {ordered} » par un second chemin.
        for (String regle : association.constraints()) {
            parties.add("{" + PlantUmlEscaper.label(regle) + "}");
        }
        return assembler(parties.build());
    }

    // Ajoute un morceau s'il a quelque chose à dire, échappé et mis en forme.
    //
    // Le gabarit porte la ponctuation propre au morceau — la barre d'un rôle dérivé — pour que
    // l'échappement reste appliqué au seul texte de l'utilisateur, et jamais à la notation que
    // nous produisons autour.
    private static void ajouter(ImmutableList.Builder<String> parties, String texte,
        String gabarit) {
        if (texte.isEmpty()) {
            return;
        }
        parties.add(gabarit.formatted(PlantUmlEscaper.label(texte)));
    }

    // Les morceaux absents ayant simplement été omis, le join ne laisse aucun espace en trop.
    private static String assembler(ImmutableList<String> parties) {
        return String.join(" ", parties);
    }
}
