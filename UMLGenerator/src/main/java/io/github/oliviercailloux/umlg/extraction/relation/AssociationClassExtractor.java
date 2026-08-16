package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlAssociationClass;

public final class AssociationClassExtractor implements RelationExtractor {
    @Override
    public ImmutableList<UmlAssociationClass> extract(Class<?> clazz, Rules rules) {
        UMLAssociationClass annotation = clazz.getAnnotation(UMLAssociationClass.class);
        if (annotation == null) {
            return ImmutableList.of();
        }
        String context = "@UMLAssociationClass on " + clazz.getName();

        checkArgument(annotation.source() != clazz,
            messageReflexiveEndpoint(context, "source()"));
        checkArgument(annotation.target() != clazz,
            messageReflexiveEndpoint(context, "target()"));

        TargetResolver.validateTarget(annotation.source(), context, "source()");
        TargetResolver.validateTarget(annotation.target(), context, "target()");

        return ImmutableList.of(new UmlAssociationClass(annotation.source().getName(),
            annotation.target().getName()));
    }

    public static String messageReflexiveEndpoint(String context, String member) {
        return context + ": " + member + " is the association class itself, which qualifies the "
            + "link and cannot also be an end of it — the link would be attached to the very box "
            + "that qualifies it. Point " + member + " at one of the two classes the link joins.";
    }
}
