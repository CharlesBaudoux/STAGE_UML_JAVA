package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlPackage;

final class PackageExtractor {

    private PackageExtractor() {}

    static UmlPackage extraire(Class<?> clazz) {
        return extraire(clazz, Regles.aucune());
    }

    static UmlPackage extraire(Class<?> clazz, Regles regles) {
        checkNotNull(regles);
        UMLPackage annotation = clazz.getAnnotation(UMLPackage.class);
        if (annotation == null) {
            return UmlPackage.aucun();
        }
        String contexte = "@UMLPackage on " + clazz.getName();

        checkArgument(!annotation.name().isBlank(),
            "%s: name() cannot be empty or made of whitespace only; a group is identified by "
                + "its name.",
            contexte);

        ClassHeaderValidator.validateColor(annotation.color(), contexte);

        return new UmlPackage(annotation.name(), annotation.color());
    }
}
