package io.github.oliviercailloux.umlg.processing;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

final class Member {
    private Member() {}

    static int bits(Element member) {
        int bits = 0;
        for (Modifier modifier : member.getModifiers()) {
            switch (modifier) {
                case PUBLIC -> bits |= java.lang.reflect.Modifier.PUBLIC;
                case PROTECTED -> bits |= java.lang.reflect.Modifier.PROTECTED;
                case PRIVATE -> bits |= java.lang.reflect.Modifier.PRIVATE;
                default -> { }
            }
        }
        return bits;
    }

    static boolean isAttribute(Element member) {
        return member.getKind() == ElementKind.FIELD
            || member.getKind() == ElementKind.ENUM_CONSTANT;
    }

    static boolean comesFromComponent(Element member) {
        Element holder = member.getEnclosingElement();
        if (holder == null || holder.getKind() != ElementKind.RECORD) {
            return false;
        }
        if (member.getKind() != ElementKind.FIELD
            && member.getKind() != ElementKind.METHOD) {
            return false;
        }
        return holder.getEnclosedElements().stream()
            .filter(element -> element.getKind() == ElementKind.RECORD_COMPONENT)
            .anyMatch(element -> element.getSimpleName().contentEquals(member.getSimpleName()));
    }

    static String location(Element member) {
        return ((TypeElement) member.getEnclosingElement()).getQualifiedName() + "."
            + member.getSimpleName();
    }
}
