package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import java.util.HashSet;
import java.util.Set;

// Relit les @UMLDependency posées sur la classe elle-même.
//
// Plus simple que l'extraction des associations : il n'y a rien à déduire. Le seul travail est de
// détecter les doublons, qu'aucun attribut ne distingue ici — deux dépendances identiques
// dessineraient deux traits superposés.
public final class DependencyExtractor implements RelationExtractor {

    @Override
    public ImmutableList<UmlDependency> extraire(Class<?> clazz) {
        ImmutableList.Builder<UmlDependency> result = ImmutableList.builder();

        Set<UmlDependency> deja = new HashSet<>();

        for (UMLDependency annotation : clazz.getAnnotationsByType(UMLDependency.class)) {
            Class<?> cible = annotation.target();
            String contexte = "@UMLDependency sur " + clazz.getName();

            checkArgument(cible != clazz,
                "%s: a class cannot depend on itself; the relation would teach the diagram's "
                    + "reader nothing.",
                contexte);

            TargetResolver.validerCible(cible, contexte, "target()");

            UmlDependency dependance;
            try {
                dependance = new UmlDependency(cible.getName(), annotation.label());
            } catch (IllegalArgumentException | NullPointerException e) {

                throw new IllegalArgumentException(contexte + " : " + e.getMessage(), e);
            }

            checkArgument(deja.add(dependance),
                "%s: the dependency towards %s is declared twice with the same label; the two "
                    + "lines would overlap with nothing to show it.",
                contexte, cible.getName());

            result.add(dependance);
        }
        return result.build();
    }
}
