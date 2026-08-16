package io.github.oliviercailloux.umlg.extraction.relation;

import java.lang.reflect.Field;

final class Location {
    private Location() {}

    static String of(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }
}
