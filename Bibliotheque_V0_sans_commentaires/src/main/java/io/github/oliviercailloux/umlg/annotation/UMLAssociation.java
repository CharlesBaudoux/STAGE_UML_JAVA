package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Met en avant un attribut en lien entre deux Class du diagramme.
//
// Règle UML : un attribut ne doit jamais s'afficher en double sur le diagramme.
// Il s'écrit SOIT comme un texte à l'intérieur de la boîte, SOIT comme une flèche reliant deux
// boîtes.
// Par conséquent, si cette annotation est utilisée pour créer une flèche,
// l'outil masque automatiquement l'attribut à l'intérieur de la boîte pour éviter le doublon.
//
// FIELD : l'annotation se pose sur un attribut ; c'est lui qui porte le type de la cible.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface UMLAssociation {

    Class<?> target();

    UMLRelationType type() default UMLRelationType.ASSOCIATION;

    String sourceCardinality() default "";

    String targetCardinality() default "";

    String sourceRole() default "";

    String targetRole() default "";

    boolean isOrdered() default false;

    String qualifier() default "";

    String label() default "";

    UMLDirection direction() default UMLDirection.NONE;
}
