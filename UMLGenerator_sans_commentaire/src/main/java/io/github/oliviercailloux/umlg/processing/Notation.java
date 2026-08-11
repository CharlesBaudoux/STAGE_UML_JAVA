package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import javax.lang.model.element.AnnotationMirror;

final class Notation {

    private Notation() {}

    static boolean trait(Rapporteur rapporteur, AnnotationMirror association) {
        return !UMLNotation.ATTRIBUTE.name().equals(rapporteur.texte(association, "notation"));
    }
}
