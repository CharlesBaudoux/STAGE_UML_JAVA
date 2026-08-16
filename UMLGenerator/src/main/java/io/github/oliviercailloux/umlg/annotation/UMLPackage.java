package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Files the class in a named group, drawn as a rectangle surrounding it. The group is
 * declared, never inferred from the Java package.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLPackage {
    /**
     * {@return the name written on the rectangle. Mandatory: two classes with the same group name
     * * retrouvent}
     */
    String name();

    /** {@return the background colour of the rectangle, a PlantUML name or #RRGGBB} */
    String color() default "";
}
