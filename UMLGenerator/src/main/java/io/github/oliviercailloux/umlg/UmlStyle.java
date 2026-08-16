package io.github.oliviercailloux.umlg;

/**
 * Who decides the colours, PlantUML or the library. The choice never changes what the
 * diagram says.
 */
public enum UmlStyle {
    /** PlantUML's theme, unchanged: the default, because it is the least presumptuous. */
    PLANTUML,

    /** The library's own theme. */
    UMLG
}
