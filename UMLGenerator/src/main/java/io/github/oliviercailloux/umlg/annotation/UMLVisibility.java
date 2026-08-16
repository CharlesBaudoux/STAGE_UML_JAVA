package io.github.oliviercailloux.umlg.annotation;

/**
 * How far down the Java visibilities to go when deciding what enters the box;
 * &#64;UMLInclude and &#64;UMLExclude win member by member.
 */
public enum UMLVisibility {
    /**
     * The strategy declared by the rules file, or NONE if it declares none: the default
     * of &#64;UMLClass, and this value exists for it only.
     */
    PROJECT,

    /** No member: the box carries its name only. */
    NONE,

    /** Les members publics. */
    PUBLIC,

    /** Public and protected members. */
    PROTECTED,

    /** Everything but private members. */
    PACKAGE,

    /** Every member. */
    PRIVATE
}
