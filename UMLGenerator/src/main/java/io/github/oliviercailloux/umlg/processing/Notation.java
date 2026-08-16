package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import javax.lang.model.element.AnnotationMirror;

final class Notation {
    private Notation() {}

    static boolean stroke(Reporter reporter, AnnotationMirror association) {
        return !UMLNotation.ATTRIBUTE.name().equals(reporter.text(association, "notation"));
    }
}
