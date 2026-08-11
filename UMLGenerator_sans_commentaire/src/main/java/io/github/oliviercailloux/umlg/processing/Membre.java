package io.github.oliviercailloux.umlg.processing;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

final class Membre {

    private Membre() {}

    static int bits(Element membre) {
        int bits = 0;
        for (Modifier modificateur : membre.getModifiers()) {
            switch (modificateur) {
                case PUBLIC -> bits |= java.lang.reflect.Modifier.PUBLIC;
                case PROTECTED -> bits |= java.lang.reflect.Modifier.PROTECTED;
                case PRIVATE -> bits |= java.lang.reflect.Modifier.PRIVATE;
                default -> { }
            }
        }
        return bits;
    }

    static boolean estAttribut(Element membre) {
        return membre.getKind() == ElementKind.FIELD
            || membre.getKind() == ElementKind.ENUM_CONSTANT;
    }

    static boolean vientDUnComposant(Element membre) {
        Element porteuse = membre.getEnclosingElement();
        if (porteuse == null || porteuse.getKind() != ElementKind.RECORD) {
            return false;
        }
        if (membre.getKind() != ElementKind.FIELD
            && membre.getKind() != ElementKind.METHOD) {
            return false;
        }
        return porteuse.getEnclosedElements().stream()
            .filter(element -> element.getKind() == ElementKind.RECORD_COMPONENT)
            .anyMatch(element -> element.getSimpleName().contentEquals(membre.getSimpleName()));
    }

    static String emplacement(Element membre) {
        return ((TypeElement) membre.getEnclosingElement()).getQualifiedName() + "."
            + membre.getSimpleName();
    }
}
