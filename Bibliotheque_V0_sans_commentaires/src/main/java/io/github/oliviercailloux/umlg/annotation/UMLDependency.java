package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Déclare une dépendance : « cette classe se sert de cette autre », sans qu'un attribut ne la
// détienne. Dessinée en pointillés fléchés. Typiquement : un paramètre de méthode, un type de
// retour, une classe utilitaire appelée en statique.
//
// L'annotation se pose sur la classe (et non sur un membre) parce qu'aucun élément précis du
// code ne porte la relation : c'est la classe entière qui dépend de l'autre.
//
// @Repeatable : on peut en poser plusieurs sur la même classe ; le compilateur les regroupe
// alors automatiquement dans le conteneur @UMLDependencies.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(UMLDependencies.class)
public @interface UMLDependency {

    Class<?> target();

    String label() default "";
}
