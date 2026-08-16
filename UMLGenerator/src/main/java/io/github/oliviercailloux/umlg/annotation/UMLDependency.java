package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares that the class uses another without holding it; drawn as a dotted arrow. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(UMLDependencies.class)
public @interface UMLDependency {
    /** {@return the class depended upon. Mandatory} */
    Class<?> target();

    /** {@return the text written along the arrow} */
    String label() default "";
}
