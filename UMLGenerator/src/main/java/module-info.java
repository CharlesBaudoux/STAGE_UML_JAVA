/**
 * The library as a unit of encapsulation: three exported packages — the entry point,
 * the vocabulary the user writes in their code, and the model they can inspect.
 * Everything else is closed.
 */
module io.github.oliviercailloux.umlg {
    requires transitive com.google.common;

    requires net.sourceforge.plantuml;

    requires com.fasterxml.jackson.databind;

    requires java.compiler;

    exports io.github.oliviercailloux.umlg;

    exports io.github.oliviercailloux.umlg.annotation;

    exports io.github.oliviercailloux.umlg.model;

    provides javax.annotation.processing.Processor
        with io.github.oliviercailloux.umlg.processing.UmlgProcessor;
}
