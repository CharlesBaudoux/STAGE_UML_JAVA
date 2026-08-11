package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.model.UmlPackage;

// Détermine le groupe visuel d'une classe : celui, et celui-là seul, qu'elle déclare.
//
// POURQUOI AUCUNE RÈGLE NE RANGE UNE CLASSE
//
// Le fichier de règles a porté un « group package com.exemple.modele -> "Modèle" », qui rangeait
// tout un paquetage d'une ligne. Il est parti avec la question qui décide de tout partage entre
// une annotation et une règle : la décision désigne-t-elle un élément par son nom ? Un paquetage
// est un nom, et un rectangle nommé posé sur des classes nommées est un cas particulier, pas un
// réglage de projet. @UMLPackage le dit sur l'élément, avec sa couleur en prime.
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
