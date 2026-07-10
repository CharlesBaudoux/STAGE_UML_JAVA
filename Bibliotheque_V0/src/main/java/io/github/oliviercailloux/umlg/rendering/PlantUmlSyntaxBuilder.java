package io.github.oliviercailloux.umlg.rendering;

import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;

public class PlantUmlSyntaxBuilder {

    private final StringBuilder builder;

    public PlantUmlSyntaxBuilder() {
        this.builder = new StringBuilder();
    }

    public void startDocument() {
        builder.append("@startuml\n");
        // Désactive les petites icônes rondes de PlantUML pour forcer l'affichage 
        // des vrais symboles UML standards (+, -, #, ~)
        builder.append("skinparam classAttributeIconSize 0\n\n"); 
    }

    public void endDocument() {
        builder.append("@enduml\n");
    }

    public PlantUmlSyntaxBuilder addClass(UmlEntity entity) {
        // 1. Déclaration de la classe et de son nom
        // On crée un alias sans espaces pour éviter les bugs PlantUML avec le mot-clé 'as'
        String alias = entity.name().replaceAll("[^a-zA-Z0-9]", "");
        builder.append("class \"").append(entity.name()).append("\" as ").append(alias);

        
        if (entity.stereotype() != null && !entity.stereotype().isBlank()) {
            builder.append(" <<").append(entity.stereotype()).append(">>");
        }

        
        if (entity.color() != null && !entity.color().isBlank()) {
            String colorCode = entity.color().startsWith("#") ? entity.color() : "#" + entity.color();
            builder.append(" ").append(colorCode);
        }

        builder.append(" {\n");

        
        for (UmlField field : entity.fields()) {
            builder.append("  ").append(field.visibility()).append(" ");
            
            if (field.isStatic()) {
                builder.append("{static} ");
            }
            
            builder.append(field.type()).append(" ").append(field.name()).append("\n");
        }

       
        if (!entity.fields().isEmpty() && !entity.methods().isEmpty()) {
            builder.append("  --\n");
        }

       
        for (UmlMethod method : entity.methods()) {
            builder.append("  ").append(method.visibility()).append(" ");
            
            if (method.isStatic()) {
                builder.append("{static} ");
            }
            
            builder.append(method.returnType()).append(" ").append(method.name()).append("(");
            builder.append(String.join(", ", method.parameterTypes()));
            builder.append(")\n");
        }

        builder.append("}\n\n");
        return this;
    }

    public String build() {
        return builder.toString();
    }
}