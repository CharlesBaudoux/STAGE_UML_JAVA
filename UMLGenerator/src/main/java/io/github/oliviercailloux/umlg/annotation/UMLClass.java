package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Brings the class into the diagram: without it, the scanner ignores it. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLClass {
    /** {@return the name displayed in the box; empty = the simple name of the class} */
    String name() default "";

    /** {@return the background colour, a PlantUML name or #RRGGBB; empty = the theme's own} */
    String color() default "";

    /** {@return the stereotype written above the name, without its quotes} */
    String stereotype() default "";

    /** {@return how far down the visibilities to go when filling the box} */
    UMLVisibility strategy() default UMLVisibility.PROJECT;
}
