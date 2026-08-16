package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import java.lang.reflect.AnnotatedElement;

public final class ConstraintExtractor {
    private ConstraintExtractor() {}

    public static ImmutableList<String> of(AnnotatedElement element, String location) {
        ImmutableList.Builder<String> result = ImmutableList.builder();

        for (UMLConstraint annotation : element.getAnnotationsByType(UMLConstraint.class)) {
            String rule = annotation.rule();

            checkArgument(!rule.isBlank(),
                "@UMLConstraint on %s : rule() cannot be empty or made of whitespace only.",
                location);
            result.add(rule);
        }
        return result.build();
    }
}
