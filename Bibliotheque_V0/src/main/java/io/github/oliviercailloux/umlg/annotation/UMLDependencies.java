package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Conteneur technique de @UMLDependency, exigé par le mécanisme @Repeatable de Java.
//
// L'utilisateur n'écrit jamais cette annotation lui-même : quand il pose plusieurs
// @UMLDependency sur une classe, le compilateur les emballe ici. Côté extraction, on ne la lit
// pas non plus directement — getAnnotationsByType(UMLDependency.class) déballe le conteneur.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLDependencies {

    // Le tableau des annotations regroupées. Le nom « value » est imposé par @Repeatable.
    UMLDependency[] value();
}
