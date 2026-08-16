package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.model.UmlPackage;

public final class PackageExtractor {
    private PackageExtractor() {}

    static UmlPackage extract(Class<?> clazz) {
        UMLPackage annotation = clazz.getAnnotation(UMLPackage.class);
        if (annotation == null) {
            return UmlPackage.none();
        }
        String context = "@UMLPackage on " + clazz.getName();

        checkArgument(!annotation.name().isBlank(), messageNomDeGroupeVide(context));

        ClassHeaderValidator.validateColor(annotation.color(), context);

        return new UmlPackage(annotation.name(), annotation.color());
    }

    public static String messageNomDeGroupeVide(String context) {
        return context + ": name() cannot be empty or made of whitespace only; a group is "
            + "identified by its name.";
    }
}
