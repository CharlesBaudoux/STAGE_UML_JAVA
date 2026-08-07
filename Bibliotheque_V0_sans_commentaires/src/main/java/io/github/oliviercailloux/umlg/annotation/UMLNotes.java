package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Conteneur technique de @UMLNote, exigé par le mécanisme @Repeatable de Java.
//
// L'utilisateur ne l'écrit jamais : le compilateur y emballe les @UMLNote multiples, et
// getAnnotationsByType les en ressort. Voir UMLDependencies, qui joue le même rôle.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface UMLNotes {

    UMLNote[] value();
}
