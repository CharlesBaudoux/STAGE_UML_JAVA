package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlAssociationClass;

public final class AssociationClassExtractor implements RelationExtractor {

    @Override
    public ImmutableList<UmlAssociationClass> extraire(Class<?> clazz, Regles regles) {
        UMLAssociationClass annotation = clazz.getAnnotation(UMLAssociationClass.class);
        if (annotation == null) {
            return ImmutableList.of();
        }
        String contexte = "@UMLAssociationClass sur " + clazz.getName();

        checkArgument(annotation.source() != clazz,
            messageExtremiteReflexive(contexte, "source()"));
        checkArgument(annotation.target() != clazz,
            messageExtremiteReflexive(contexte, "target()"));

        TargetResolver.validerCible(annotation.source(), contexte, "source()");
        TargetResolver.validerCible(annotation.target(), contexte, "target()");

        return ImmutableList.of(new UmlAssociationClass(annotation.source().getName(),
            annotation.target().getName()));
    }

    public static String messageExtremiteReflexive(String contexte, String membre) {
        return contexte + ": " + membre + " is the association class itself, which qualifies the "
            + "link and cannot also be an end of it — the link would be attached to the very box "
            + "that qualifies it. Point " + membre + " at one of the two classes the link joins.";
    }
}
