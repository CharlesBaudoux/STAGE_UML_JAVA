package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

public final class TargetResolver {
    private TargetResolver() {}

    public static void validateTarget(Class<?> target, String context, String parameter) {
        checkArgument(!target.isPrimitive(),
            messagePrimitiveTarget(context, target.getName()));

        checkArgument(target.isAnnotationPresent(UMLClass.class),
            messageTargetOutsideDiagram(context, target.getName(), parameter));
    }

    public static String messagePrimitiveTarget(String context, String typeName) {
        return context + ": a relation cannot target the primitive type " + typeName
            + "; only a class of the diagram can be an endpoint.";
    }

    public static String messageTargetOutsideDiagram(String context, String targetName,
        String parameter) {
        return context + ": target " + targetName + " does not carry @UMLClass, so it is not "
            + "part of the diagram. Annotate it, or point " + parameter + " at a class that is.";
    }
}
