package io.github.oliviercailloux.umlg.extraction.relation;

import java.lang.reflect.Field;

// Désigne un attribut dans un message d'erreur, sous la forme « com.banque.Client.commandes ».

final class Emplacement {

    private Emplacement() {}

    static String de(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }
}
