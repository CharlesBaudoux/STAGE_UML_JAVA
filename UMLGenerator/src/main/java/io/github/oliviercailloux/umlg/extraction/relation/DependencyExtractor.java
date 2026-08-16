package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import java.util.HashSet;
import java.util.Set;

public final class DependencyExtractor implements RelationExtractor {
    @Override
    public ImmutableList<UmlDependency> extract(Class<?> clazz, Rules rules) {
        ImmutableList.Builder<UmlDependency> result = ImmutableList.builder();

        Set<UmlDependency> already = new HashSet<>();

        for (UMLDependency annotation : clazz.getAnnotationsByType(UMLDependency.class)) {
            Class<?> target = annotation.target();
            String context = "@UMLDependency on " + clazz.getName();

            checkArgument(target != clazz, messageReflexiveDependency(context));

            TargetResolver.validateTarget(target, context, "target()");

            UmlDependency dependency;
            try {
                dependency = new UmlDependency(target.getName(), annotation.label());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException(context + " : " + e.getMessage(), e);
            }

            checkArgument(already.add(dependency),
                duplicateDependencyMessage(context, target.getName()));

            result.add(dependency);
        }
        return result.build();
    }

    public static String messageReflexiveDependency(String context) {
        return context + ": a class cannot depend on itself; the relation would teach the "
            + "diagram's reader nothing.";
    }

    public static String duplicateDependencyMessage(String context, String targetName) {
        return context + ": the dependency towards " + targetName + " is declared twice with "
            + "the same label; the two lines would overlap with nothing to show it.";
    }
}
