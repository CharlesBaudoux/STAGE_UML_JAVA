package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlPackage;

// Détermine le groupe visuel d'une classe : celui qu'elle déclare, ou celui de son paquetage.
//
// L'annotation l'emporte sur la règle, comme partout : le cas particulier, écrit sur l'élément,
// bat le réglage général du projet.
//
// Aussi court que ClassHeaderValidator, et pour la même raison : il n'y a rien à déduire, tout
// est déclaré. Ce qui vaut d'être écrit ici est la validation, dont les messages nomment la
// classe fautive — le modèle, lui, ne la connaît pas.
final class PackageExtractor {

    private PackageExtractor() {}

    // Sans règles de projet : le cas d'une classe extraite isolément, et celui des tests.
    static UmlPackage extraire(Class<?> clazz) {
        return extraire(clazz, Regles.aucune());
    }

    static UmlPackage extraire(Class<?> clazz, Regles regles) {
        UMLPackage annotation = clazz.getAnnotation(UMLPackage.class);
        if (annotation == null) {
            // Rien sur la classe : c'est peut-être son paquetage entier qui est rangé, par une
            // ligne « group package … » du fichier. Une seule ligne remplace alors l'annotation
            // sur chacune des classes du paquetage — ce qu'aucun projet ne tient à jour à la
            // main. Le rectangle n'a pas de couleur : @UMLPackage reste le moyen d'en donner une.
            String groupe = regles.groupeDuPaquetage(clazz.getPackageName());
            return groupe.isEmpty() ? UmlPackage.aucun() : new UmlPackage(groupe, "");
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
