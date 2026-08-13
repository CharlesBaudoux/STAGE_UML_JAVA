package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import java.util.HashSet;
import java.util.Set;

public final class DependencyExtractor implements RelationExtractor {

    @Override
    public ImmutableList<UmlDependency> extraire(Class<?> clazz, Regles regles) {
        ImmutableList.Builder<UmlDependency> result = ImmutableList.builder();

        Set<UmlDependency> deja = new HashSet<>();

        for (UMLDependency annotation : clazz.getAnnotationsByType(UMLDependency.class)) {
            Class<?> cible = annotation.target();
            String contexte = "@UMLDependency sur " + clazz.getName();

            checkArgument(cible != clazz, messageDependanceReflexive(contexte));

            TargetResolver.validerCible(cible, contexte, "target()");

            UmlDependency dependance;
            try {
                dependance = new UmlDependency(cible.getName(), annotation.label());
            } catch (IllegalArgumentException | NullPointerException e) {

                throw new IllegalArgumentException(contexte + " : " + e.getMessage(), e);
            }

            checkArgument(deja.add(dependance),
                messageDependanceEnDouble(contexte, cible.getName()));

            result.add(dependance);
        }
        return result.build();
    }


    public static String messageDependanceReflexive(String contexte) {
        return contexte + ": a class cannot depend on itself; the relation would teach the "
            + "diagram's reader nothing.";
    }

    public static String messageDependanceEnDouble(String contexte, String nomDeLaCible) {
        return contexte + ": the dependency towards " + nomDeLaCible + " is declared twice with "
            + "the same label; the two lines would overlap with nothing to show it.";
    }
}
