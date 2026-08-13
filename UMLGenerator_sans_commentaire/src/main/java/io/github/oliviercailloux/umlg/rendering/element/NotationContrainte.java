package io.github.oliviercailloux.umlg.rendering.element;

import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

import java.util.List;

final class NotationContrainte {

    private NotationContrainte() {}

    static String une(String regle) {
        return "{" + PlantUmlEscaper.label(regle) + "}";
    }

    static String aLaSuite(List<String> regles) {
        StringBuilder sortie = new StringBuilder();
        for (String regle : regles) {
            sortie.append(' ').append(une(regle));
        }
        return sortie.toString();
    }
}
