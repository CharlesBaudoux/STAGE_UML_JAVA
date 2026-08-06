package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.model.UmlPackage;

// Relit l'@UMLPackage posée sur la classe, et en tire son groupe.
//
// Aussi court que ClassHeaderValidator, et pour la même raison : il n'y a rien à déduire, tout
// est déclaré. Ce qui vaut d'être écrit ici est la validation, dont les messages nomment la
// classe fautive — le modèle, lui, ne la connaît pas.
final class PackageExtractor {

    private PackageExtractor() {}

    static UmlPackage extraire(Class<?> clazz) {
        UMLPackage annotation = clazz.getAnnotation(UMLPackage.class);
        if (annotation == null) {
            return UmlPackage.aucun();
        }
        String contexte = "@UMLPackage on " + clazz.getName();

        // name() n'a pas de valeur par défaut : javac exige donc qu'il soit écrit. Reste le cas
        // qu'il ne peut pas voir, celui d'un nom vide ou fait d'espaces — un rectangle dont
        // l'étiquette ne se lirait pas.
        checkArgument(!annotation.name().isBlank(),
            "%s: name() cannot be empty or made of whitespace only; a group is identified by "
                + "its name.",
            contexte);

        // La couleur est confiée au même contrôle que celle d'une boîte : c'est la même
        // grammaire, et il n'y a aucune raison qu'un rectangle accepte ce qu'une boîte refuse.
        ClassHeaderValidator.validateColor(annotation.color(), contexte);

        return new UmlPackage(annotation.name(), annotation.color());
    }
}
