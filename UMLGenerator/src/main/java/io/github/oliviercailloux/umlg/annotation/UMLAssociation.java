package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Draws the field as a link towards another box, rather than as a line. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface UMLAssociation {
    /** {@return the class at the other end; by default the one the field type names} */
    Class<?> target() default void.class;

    /** {@return a stroke between the boxes, or a line in the starting box} */
    UMLNotation notation() default UMLNotation.ARROW;

    /** {@return the nature of the link: association, composition or aggregation} */
    UMLRelationType type() default UMLRelationType.ASSOCIATION;

    /** {@return the multiplicity written at the source endpoint, "1" or "0..*"} */
    String sourceMultiplicity() default "";

    /** {@return the multiplicity written at the target endpoint} */
    String targetMultiplicity() default "";

    /** {@return the role written at the source endpoint} */
    String sourceRole() default "";

    /** {@return the role written at the target endpoint} */
    String targetRole() default "";

    /** {@return the text written along the stroke} */
    String label() default "";

    /** {@return which side of the source to push the target box to} */
    UMLDirection direction() default UMLDirection.NONE;
}
