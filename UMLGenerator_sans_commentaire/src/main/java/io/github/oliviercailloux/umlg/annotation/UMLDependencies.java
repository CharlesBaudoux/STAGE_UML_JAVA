package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conteneur technique de @UMLDependency, exigé par le mécanisme @Repeatable de Java.
 *
 * <p>L'utilisateur n'écrit jamais cette annotation lui-même : quand il pose plusieurs
 * &#64;UMLDependency sur une classe, le compilateur les emballe ici. Côté extraction, on ne la lit
 * pas non plus directement — getAnnotationsByType(UMLDependency.class) déballe le conteneur.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLDependencies {

    /**
     * {@return les dépendances regroupées}. Le nom {@code value} est imposé par
     * {@code @Repeatable}.
     */
    UMLDependency[] value();
}
