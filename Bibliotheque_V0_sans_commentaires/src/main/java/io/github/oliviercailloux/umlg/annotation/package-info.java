// Le vocabulaire que l'utilisateur écrit dans son propre code.
//
// Ce paquetage ne contient que des annotations et des énumérations : rien n'y calcule quoi que
// ce soit. C'est la seule partie de la bibliothèque que l'utilisateur voit apparaître dans ses
// classes (@UMLClass, @UMLAssociation, @UMLDependency, @UMLInclude, @UMLExclude).
//
// Toutes les annotations sont en RetentionPolicy.RUNTIME : sans cela elles disparaîtraient à la
// compilation, et l'extraction — qui travaille par réflexion sur les classes déjà chargées — ne
// pourrait plus les lire.
package io.github.oliviercailloux.umlg.annotation;
