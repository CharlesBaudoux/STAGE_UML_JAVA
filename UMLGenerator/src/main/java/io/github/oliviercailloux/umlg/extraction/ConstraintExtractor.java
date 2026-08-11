package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import java.lang.reflect.AnnotatedElement;

// Lit les @UMLConstraint posées sur un élément, quel qu'il soit.
//
// Une classe, un attribut, un attribut promu en lien : les trois se lisent de la même façon, et
// seule leur destination diffère. D'où le paramètre AnnotatedElement, l'ancêtre commun de Class
// et de Field, qui permet à ce code d'être écrit une fois pour les trois appelants.
//
// Public parce que ces appelants vivent dans trois paquetages différents (extraction,
// extraction.member, extraction.relation) et que Java n'accorde aucun privilège d'accès entre un
// paquetage et ses voisins. Le module, lui, n'exporte rien de tout cela.
public final class ConstraintExtractor {

    private ConstraintExtractor() {}

    public static ImmutableList<String> de(AnnotatedElement element, String emplacement) {
        ImmutableList.Builder<String> result = ImmutableList.builder();

        // getAnnotationsByType déballe le conteneur créé par @Repeatable : qu'il y en ait une ou
        // dix, on récupère un tableau uniforme.
        for (UMLConstraint annotation : element.getAnnotationsByType(UMLConstraint.class)) {
            String regle = annotation.rule();
            // La règle est vérifiée ici plutôt que dans le modèle pour la seule raison qui
            // vaille : le message peut nommer l'élément fautif, ce que le modèle ne saurait
            // faire. La règle elle-même reste celle qu'appliquent UmlField, UmlEntity et
            // UmlAssociation, qui la revérifient à la construction.
            checkArgument(!regle.isBlank(),
                "@UMLConstraint sur %s : rule() cannot be empty or made of whitespace only.",
                emplacement);
            result.add(regle);
        }
        return result.build();
    }
}
