package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Attaches a comment bubble to a class, a field or a method. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Repeatable(UMLNotes.class)
public @interface UMLNote {
    /** {@return the content of the bubble. Mandatory and non-empty} */
    String text();

    /** {@return the side the bubble is placed on; NONE lets the library choose the right} */
    UMLDirection direction() default UMLDirection.NONE;
}
