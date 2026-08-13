package io.github.oliviercailloux.umlg.extraction.relation;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.extraction.ConstraintExtractor;
import io.github.oliviercailloux.umlg.extraction.MembresOrdonnes;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import java.lang.reflect.Field;

public final class AssociationExtractor implements RelationExtractor {

    @Override
    public ImmutableList<UmlAssociation> extraire(Class<?> clazz) {
        return extraire(clazz, Regles.aucune());
    }

    @Override
    public ImmutableList<UmlAssociation> extraire(Class<?> clazz, Regles regles) {
        ImmutableList.Builder<UmlAssociation> result = ImmutableList.builder();

        for (Field field : MembresOrdonnes.champs(clazz, regles)) {
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
            result.add(traduire(field, annotation, field.isAnnotationPresent(UMLDerived.class)));
        }
        return result.build();
    }

    private static UmlAssociation traduire(Field field, UMLAssociation annotation,
        boolean derived) {
        String contexte = "@UMLAssociation sur " + Emplacement.de(field);
        Class<?> cible = CibleDeduite.de(field, annotation, contexte);
        TargetResolver.validerCible(cible, contexte, "target()");

        try {
            return UmlAssociation.builder(field.getName(), cible.getName())
                .type(annotation.type())
                .sourceMultiplicity(annotation.sourceMultiplicity())
                .targetMultiplicity(annotation.targetMultiplicity())
                .sourceRole(annotation.sourceRole())
                .targetRole(annotation.targetRole())
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
