package io.github.oliviercailloux.umlg.rendering.element;

import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

import java.util.List;

final class ConstraintNotation {
    private ConstraintNotation() {}

    static String one(String rule) {
        return "{" + PlantUmlEscaper.label(rule) + "}";
    }

    static String aSuite(List<String> rules) {
        StringBuilder output = new StringBuilder();
        for (String rule : rules) {
            output.append(' ').append(one(rule));
        }
        return output.toString();
    }
}
