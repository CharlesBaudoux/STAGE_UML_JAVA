package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

public final class TargetResolver {

    private TargetResolver() {}

    public static void validerCible(Class<?> cible, String contexte, String parametre) {
        checkArgument(!cible.isPrimitive(),
            "%s: a relation cannot target the primitive type %s; only a class of the diagram "
                + "can be an endpoint.",
            contexte, cible.getName());

        checkArgument(cible.isAnnotationPresent(UMLClass.class),
            "%s: target %s does not carry @UMLClass, so it is not part of the diagram. "
                + "Annotate it, or point %s at a class that is.",
            contexte, cible.getName(), parametre);
    }
}
