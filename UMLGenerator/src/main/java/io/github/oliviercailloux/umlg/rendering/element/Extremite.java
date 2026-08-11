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

    // Une étiquette composée : ce qui s'écrit dans la source, et ce qui se lit sur le dessin.
    //
    // POURQUOI DEUX TEXTES PLUTÔT QU'UN
    //
    // Ils ne se déduisent pas l'un de l'autre. Un guillemet occupe un caractère à l'écran et cinq
    // dans la source (« &#34; »), une esperluette quatre. Or l'espacement du diagramme se règle
    // sur la largeur de la plus longue étiquette : mesuré sur la source, un rôle valant
    // « l'« un » » passait pour trois fois plus large qu'il n'est, et les boîtes s'écartaient
    // pour de la place que rien n'occupe. La mesure porte donc sur affiche, l'écriture sur ecrit.
    record Etiquette(String ecrit, String affiche) {

        boolean estVide() {
            return ecrit.isEmpty();
        }
    }

    private Extremite() {}

    // Côté source : rôle, multiplicité — dans cet ordre, celui que lit l'œil de gauche à droite.
    static Etiquette source(UmlAssociation association) {
        Morceaux morceaux = new Morceaux();

        morceaux.ajouter(association.sourceRole(), "%s");
        morceaux.ajouter(association.sourceCardinality(), "%s");

        return morceaux.assembler();
    }

    // Côté cible : rôle, multiplicité, puis les contraintes.
    static Etiquette cible(UmlAssociation association) {
        Morceaux morceaux = new Morceaux();

        // La barre oblique d'un lien dérivé précède le rôle, qui est le nom de la propriété.
        morceaux.ajouter(association.targetRole(), association.derived() ? "/%s" : "%s");
        morceaux.ajouter(association.targetCardinality(), "%s");

        // Les contraintes s'écrivent toutes de la même façon, et c'est voulu : « {subset} » écrit
        // par l'auteur ne se distingue pas d'un « {ordered} » sur le dessin, et n'a aucune raison
        // de le faire. C'est ce qui a permis de retirer le paramètre isOrdered, qui produisait ce
        // même « {ordered} » par un second chemin. La notation elle-même vit dans
        // NotationContrainte, seul endroit où les accolades sont posées.
        for (String regle : association.constraints()) {
            morceaux.ajouterTelQuel(NotationContrainte.une(regle), "{" + regle + "}");
        }
        return morceaux.assembler();
    }

    // Les morceaux de l'étiquette en cours de composition, tenus en deux exemplaires.
    //
    // Les deux listes avancent ensemble, morceau par morceau : c'est ce qui garantit qu'aucune
    // écriture n'échappe à la mesure, et qu'aucune mesure ne porte sur un morceau qui ne sera pas
    // écrit.
    private static final class Morceaux {

        private final ImmutableList.Builder<String> ecrits = ImmutableList.builder();
        private final ImmutableList.Builder<String> affiches = ImmutableList.builder();

        // Ajoute un morceau s'il a quelque chose à dire, échappé et mis en forme.
        //
        // Le gabarit porte la ponctuation propre au morceau — la barre d'un rôle dérivé — pour que
        // l'échappement reste appliqué au seul texte de l'utilisateur, et jamais à la notation que
        // nous produisons autour.
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

        // Les morceaux absents ayant simplement été omis, le join ne laisse aucun espace en trop.
        Etiquette assembler() {
            return new Etiquette(String.join(" ", ecrits.build()),
                String.join(" ", affiches.build()));
        }
    }
}
