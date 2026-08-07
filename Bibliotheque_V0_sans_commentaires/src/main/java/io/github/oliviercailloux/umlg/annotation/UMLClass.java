package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marque une classe comme faisant partie du diagramme.
//
// C'est l'annotation pivot : une classe qui ne la porte pas est ignorée par le scanner, et ne
// peut pas non plus servir de cible à une relation (voir TargetResolver.validerCible).
//
// RUNTIME : l'annotation doit survivre à la compilation, l'extraction la lit par réflexion.
// TYPE : elle se pose sur une classe, une interface, un enum ou un record.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLClass {

    String name() default "";

    String color() default "";

    String stereotype() default "";

    UMLVisibility strategy() default UMLVisibility.NONE;
}
