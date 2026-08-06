package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Le conteneur de @UMLLayout, exigé par @Repeatable. Le compilateur l'écrit lui-même dès qu'une
// classe porte plusieurs @UMLLayout ; personne ne l'écrit à la main.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLLayouts {

    UMLLayout[] value();
}
