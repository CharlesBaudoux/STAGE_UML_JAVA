package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import java.lang.reflect.AnnotatedElement;

public final class ConstraintExtractor {

    private ConstraintExtractor() {}

    public static ImmutableList<String> de(AnnotatedElement element, String emplacement) {
        ImmutableList.Builder<String> result = ImmutableList.builder();

        for (UMLConstraint annotation : element.getAnnotationsByType(UMLConstraint.class)) {
            String regle = annotation.rule();
            checkArgument(!regle.isBlank(),
                "@UMLConstraint sur %s : rule() cannot be empty or made of whitespace only.",
                emplacement);
            result.add(regle);
        }
        return result.build();
    }
}
