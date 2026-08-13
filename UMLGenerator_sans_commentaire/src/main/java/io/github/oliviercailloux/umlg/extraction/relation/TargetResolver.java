package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

public final class TargetResolver {

    private TargetResolver() {}

    public static void validerCible(Class<?> cible, String contexte, String parametre) {
        checkArgument(!cible.isPrimitive(),
            messageCiblePrimitive(contexte, cible.getName()));

        checkArgument(cible.isAnnotationPresent(UMLClass.class),
            messageCibleHorsDiagramme(contexte, cible.getName(), parametre));
    }


    public static String messageCiblePrimitive(String contexte, String nomDuType) {
        return contexte + ": a relation cannot target the primitive type " + nomDuType
            + "; only a class of the diagram can be an endpoint.";
    }

    public static String messageCibleHorsDiagramme(String contexte, String nomDeLaCible,
        String parametre) {
        return contexte + ": target " + nomDeLaCible + " does not carry @UMLClass, so it is not "
            + "part of the diagram. Annotate it, or point " + parametre + " at a class that is.";
    }
}
