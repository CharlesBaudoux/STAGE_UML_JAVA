package fr.dauphine.uml;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;

/**
 * Encapsule strictement la logique de formatage textuel de PlantUML.
 */
final class PlantUmlSyntaxBuilder {

    private final StringBuilder builder;

    PlantUmlSyntaxBuilder() {
        this.builder = new StringBuilder();
    }

    PlantUmlSyntaxBuilder startDocument() {
        builder.append("@startuml\n\n");
        return this;
    }

    PlantUmlSyntaxBuilder addClass(String className, Optional<String> stereotype) {
        checkNotNull(className, "Le nom de la classe ne peut pas être null.");
        checkArgument(!className.isBlank(), "Le nom de la classe ne peut pas être vide.");

        builder.append("class ").append(className);
        
        // Si un stéréotype est présent, on l'ajoute proprement
        checkNotNull(stereotype).ifPresent(s -> builder.append(" <<").append(s).append(">>"));
        
        builder.append(" {\n");
        return this;
    }

    PlantUmlSyntaxBuilder endClass() {
        builder.append("}\n\n");
        return this;
    }

    PlantUmlSyntaxBuilder endDocument() {
        builder.append("@enduml\n");
        return this;
    }

    /**
     * @return Le code source PlantUML finalisé.
     */
    String build() {
        return builder.toString();
    }
}