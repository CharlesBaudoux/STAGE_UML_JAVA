package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the class as carrying the fields of a link between two others, which it draws at the
 * same time.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLAssociationClass {
    /** {@return the class at one endpoint of the link} */
    Class<?> source();

    /** {@return the class at the other endpoint} */
    Class<?> target();
}
