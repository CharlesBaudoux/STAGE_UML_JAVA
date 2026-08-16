package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The container the compiler fills when &#64;UMLNote is repeated. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface UMLNotes {
    /** {@return the notes set on the same element} */
    UMLNote[] value();
}
