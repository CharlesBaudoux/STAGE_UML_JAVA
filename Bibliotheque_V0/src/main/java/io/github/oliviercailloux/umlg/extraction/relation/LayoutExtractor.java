package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLLayout;
import io.github.oliviercailloux.umlg.model.UmlLayout;
import java.util.HashSet;
import java.util.Set;

// Relit les @UMLLayout posées sur la classe.
//
// Le même travail que pour les dépendances, et pour les mêmes raisons : rien à déduire, une
// cible à valider, des doublons à refuser. Deux contraintes de placement identiques ne feraient
// rien de plus qu'une, et deux contraintes contradictoires — la même cible en haut puis en bas —
// laisseraient le moteur de rendu trancher seul, ce que personne ne pourrait prévoir.
public final class LayoutExtractor implements RelationExtractor {

    @Override
    public ImmutableList<UmlLayout> extraire(Class<?> clazz) {
        ImmutableList.Builder<UmlLayout> resultat = ImmutableList.builder();

        // Les cibles déjà contraintes, et non les contraintes déjà posées : deux directions
        // différentes vers la même boîte sont justement le cas qu'il faut refuser.
        Set<String> deja = new HashSet<>();

        for (UMLLayout annotation : clazz.getAnnotationsByType(UMLLayout.class)) {
            Class<?> cible = annotation.target();
            String contexte = "@UMLLayout sur " + clazz.getName();

            checkArgument(cible != clazz,
                "%s: a class cannot be placed relative to itself.", contexte);

            TargetResolver.validerCible(cible, contexte, "target()");

            checkArgument(deja.add(cible.getName()),
                "%s: the placement of %s is declared twice; keep the one that says where you "
                    + "want it.",
                contexte, cible.getName());

            resultat.add(new UmlLayout(cible.getName(), annotation.direction()));
        }
        return resultat.build();
    }
}
