package io.github.oliviercailloux.umlg.extraction.relation;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import java.util.Arrays;

public final class InheritanceExtractor implements RelationExtractor {

    @Override
    public ImmutableList<UmlInheritance> extraire(Class<?> clazz, Regles regles) {
        ImmutableList.Builder<UmlInheritance> result = ImmutableList.builder();

        Class<?> parent = clazz.getSuperclass();
        if (parent != null && faitPartieDuDiagramme(parent)) {
            result.add(UmlInheritance.generalization(parent.getName()));
        }

        for (Class<?> interfaceDirecte : clazz.getInterfaces()) {
            if (!faitPartieDuDiagramme(interfaceDirecte)) {
                continue;
            }
            result.add(estUneSpecialisation(clazz, interfaceDirecte)
                ? UmlInheritance.generalization(interfaceDirecte.getName())
                : UmlInheritance.realization(interfaceDirecte.getName()));
        }
        return result.build();
    }

    private static boolean estUneSpecialisation(Class<?> source, Class<?> cible) {
        return source.isInterface() || estPermisePar(source, cible);
    }

    private static boolean estPermisePar(Class<?> source, Class<?> cible) {
        if (!cible.isSealed()) {
            return false;
        }
        Class<?>[] permises = cible.getPermittedSubclasses();
        if (permises == null) {
            return false;
        }
        return Arrays.asList(permises).contains(source);
    }

    private static boolean faitPartieDuDiagramme(Class<?> candidat) {
        return candidat.isAnnotationPresent(UMLClass.class);
    }
}
