package io.github.oliviercailloux.umlg.extraction.relation;

import java.lang.reflect.Field;

final class Emplacement {

    private Emplacement() {}

    static String de(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }
}
