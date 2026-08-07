package io.github.oliviercailloux.umlg.extraction.relation;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.extraction.ConstraintExtractor;
import io.github.oliviercailloux.umlg.extraction.MembresOrdonnes;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import java.lang.reflect.Field;

// Relit les attributs d'une classe et en tire les associations.
public final class AssociationExtractor implements RelationExtractor {

    @Override
    public ImmutableList<UmlAssociation> extraire(Class<?> clazz) {
        ImmutableList.Builder<UmlAssociation> result = ImmutableList.builder();

        for (Field field : MembresOrdonnes.champs(clazz)) {
            if (field.isSynthetic() || !field.isAnnotationPresent(UMLAssociation.class)) {
                continue;
            }
            if (field.isAnnotationPresent(UMLExclude.class)) {
                continue;
            }
            result.add(traduire(field, field.getAnnotation(UMLAssociation.class),
                field.isAnnotationPresent(UMLDerived.class)));
        }
        return result.build();
    }

    private static UmlAssociation traduire(Field field, UMLAssociation annotation,
        boolean derived) {
        String contexte = "@UMLAssociation sur " + Emplacement.de(field);
        Class<?> cible = annotation.target();
        TargetResolver.validerCible(cible, contexte, "target()");

        try {
            return UmlAssociation.builder(field.getName(), cible.getName())
                .type(annotation.type())
                .sourceCardinality(annotation.sourceCardinality())
                .targetCardinality(annotation.targetCardinality())
                .sourceRole(annotation.sourceRole())
                .targetRole(annotation.targetRole())
                .ordered(annotation.isOrdered())
                .qualifier(annotation.qualifier())
                .label(annotation.label())
                .direction(annotation.direction())
                .derived(derived)
                .constraints(ConstraintExtractor.de(field, Emplacement.de(field)))
                .build();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(contexte + " : " + e.getMessage(), e);
        }
    }
}
