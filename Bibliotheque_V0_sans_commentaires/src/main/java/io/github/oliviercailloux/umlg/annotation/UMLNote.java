package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Attache une bulle de commentaire à une classe, un attribut ou une méthode.
//
// Le diagramme dit le « comment » — la structure ; la note dit le « pourquoi ». C'est le seul
// endroit où une phrase en langue naturelle a sa place sur le dessin, et cela suffit à justifier
// qu'elle existe : un algorithme délicat ou une dette assumée ne se lisent dans aucune boîte.
//
// @Repeatable : plusieurs notes peuvent se poser sur le même élément.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Repeatable(UMLNotes.class)
public @interface UMLNote {

    String text();

    UMLDirection direction() default UMLDirection.NONE;
}
