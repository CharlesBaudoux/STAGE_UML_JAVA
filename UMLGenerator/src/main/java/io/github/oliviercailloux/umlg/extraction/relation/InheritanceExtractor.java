package io.github.oliviercailloux.umlg.extraction.relation;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import java.util.Arrays;

public final class InheritanceExtractor implements RelationExtractor {
    @Override
    public ImmutableList<UmlInheritance> extract(Class<?> clazz, Rules rules) {
        ImmutableList.Builder<UmlInheritance> result = ImmutableList.builder();

        Class<?> parent = clazz.getSuperclass();
        if (parent != null && isPartOfDiagram(parent)) {
            result.add(UmlInheritance.generalization(parent.getName()));
        }

        for (Class<?> directInterface : clazz.getInterfaces()) {
            if (!isPartOfDiagram(directInterface)) {
                continue;
            }
            result.add(isASpecialization(clazz, directInterface)
                ? UmlInheritance.generalization(directInterface.getName())
                : UmlInheritance.realization(directInterface.getName()));
        }
        return result.build();
    }

    private static boolean isASpecialization(Class<?> source, Class<?> target) {
        return source.isInterface() || isAllowedBy(source, target);
    }

    private static boolean isAllowedBy(Class<?> source, Class<?> target) {
        if (!target.isSealed()) {
            return false;
        }
        Class<?>[] permises = target.getPermittedSubclasses();
        if (permises == null) {
            return false;
        }

        return Arrays.asList(permises).contains(source);
    }

    private static boolean isPartOfDiagram(Class<?> candidate) {
        return candidate.isAnnotationPresent(UMLClass.class);
    }
}
