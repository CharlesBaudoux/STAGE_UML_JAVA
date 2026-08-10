package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conteneur technique de @UMLConstraint, exigé par le mécanisme @Repeatable de Java.
 *
 * <p>L'utilisateur ne l'écrit jamais. Voir UMLDependencies et UMLNotes, qui jouent le même rôle.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface UMLConstraints {

    /**
     * {@return les contraintes regroupées}. Le nom {@code value} est imposé par
     * {@code @Repeatable}.
     */
    UMLConstraint[] value();
}
