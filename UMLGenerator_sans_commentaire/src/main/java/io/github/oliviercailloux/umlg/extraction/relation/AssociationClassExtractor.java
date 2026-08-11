package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlAssociationClass;

public final class AssociationClassExtractor implements RelationExtractor {

    @Override
    public ImmutableList<UmlAssociationClass> extraire(Class<?> clazz) {
        UMLAssociationClass annotation = clazz.getAnnotation(UMLAssociationClass.class);
        if (annotation == null) {
            return ImmutableList.of();
        }
        String contexte = "@UMLAssociationClass sur " + clazz.getName();

        checkArgument(annotation.source() != clazz && annotation.target() != clazz,
            "%s: the association class cannot be one of the two ends of the link it qualifies.",
            contexte);

        TargetResolver.validerCible(annotation.source(), contexte, "source()");
        TargetResolver.validerCible(annotation.target(), contexte, "target()");

        return ImmutableList.of(new UmlAssociationClass(annotation.source().getName(),
            annotation.target().getName()));
    }
}
