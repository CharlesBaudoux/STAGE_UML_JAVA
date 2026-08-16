package io.github.oliviercailloux.umlg.extraction.relation;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.extraction.ConstraintExtractor;
import io.github.oliviercailloux.umlg.extraction.OrderedMembers;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import java.lang.reflect.Field;

public final class AssociationExtractor implements RelationExtractor {
    @Override
    public ImmutableList<UmlAssociation> extract(Class<?> clazz) {
        return extract(clazz, Rules.no());
    }

    @Override
    public ImmutableList<UmlAssociation> extract(Class<?> clazz, Rules rules) {
        ImmutableList.Builder<UmlAssociation> result = ImmutableList.builder();

        for (Field field : OrderedMembers.fields(clazz, rules)) {
            if (field.isSynthetic() || !field.isAnnotationPresent(UMLAssociation.class)) {
                continue;
            }

            if (field.isAnnotationPresent(UMLExclude.class)) {
                continue;
            }
            UMLAssociation annotation = field.getAnnotation(UMLAssociation.class);

            if (annotation.notation() != UMLNotation.ARROW) {
                continue;
            }
            result.add(translate(field, annotation, field.isAnnotationPresent(UMLDerived.class)));
        }
        return result.build();
    }

    private static UmlAssociation translate(Field field, UMLAssociation annotation,
        boolean derived) {
        String context = "@UMLAssociation on " + Location.of(field);

        Class<?> target = InferredTarget.of(field, annotation, context);
        TargetResolver.validateTarget(target, context, "target()");

        try {
            return UmlAssociation.builder(field.getName(), target.getName())
                .type(annotation.type())
                .sourceMultiplicity(annotation.sourceMultiplicity())
                .targetMultiplicity(annotation.targetMultiplicity())
                .sourceRole(annotation.sourceRole())
                .targetRole(annotation.targetRole())
                .label(annotation.label())
                .direction(annotation.direction())
                .derived(derived)

                .constraints(ConstraintExtractor.of(field, Location.of(field)))
                .build();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(context + " : " + e.getMessage(), e);
        }
    }
}
