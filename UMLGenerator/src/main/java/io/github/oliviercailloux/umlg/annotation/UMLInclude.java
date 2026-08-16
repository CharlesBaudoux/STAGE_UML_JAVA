package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Forces the display of a member that the strategy or a "hide" pattern would drop. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UMLInclude {
    /** {@return the name under which the member appears; empty = the name in the code} */
    String name() default "";

    /** {@return true to file a method in the field compartment} */
    boolean asAttribute() default false;
}
