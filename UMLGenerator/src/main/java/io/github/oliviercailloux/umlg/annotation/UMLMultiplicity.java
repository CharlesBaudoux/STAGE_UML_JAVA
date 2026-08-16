package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Says how many objects a property holds, written between brackets next to its name. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UMLMultiplicity {
    /** {@return the UML multiplicity: "1", "0..1", "*", "1..*", "2..5"} */
    String value();
}
