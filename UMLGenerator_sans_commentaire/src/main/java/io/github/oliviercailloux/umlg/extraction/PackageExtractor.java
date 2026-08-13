package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.model.UmlPackage;

public final class PackageExtractor {

    private PackageExtractor() {}

    static UmlPackage extraire(Class<?> clazz) {
        UMLPackage annotation = clazz.getAnnotation(UMLPackage.class);
        if (annotation == null) {
            return UmlPackage.aucun();
        }
        String contexte = "@UMLPackage on " + clazz.getName();

        checkArgument(!annotation.name().isBlank(), messageNomDeGroupeVide(contexte));

        ClassHeaderValidator.validateColor(annotation.color(), contexte);

        return new UmlPackage(annotation.name(), annotation.color());
    }

    public static String messageNomDeGroupeVide(String contexte) {
        return contexte + ": name() cannot be empty or made of whitespace only; a group is "
            + "identified by its name.";
    }
}
