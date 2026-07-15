package io.github.oliviercailloux.umlg.rendering;

import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.HashSet;
import java.util.Set;

public class PlantUmlSyntaxBuilder {

    private final StringBuilder builder;
    private final Set<String> aliasUtilises;

    public PlantUmlSyntaxBuilder() {
        this.builder = new StringBuilder();
        this.aliasUtilises = new HashSet<>();
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
        String alias = aliasPour(entity.name());
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

    /**
     * Alias PlantUML pour un nom affiché : un identifiant sans espace ni ponctuation, et
     * distinct de tous ceux déjà attribués dans ce document.
     *
     * <p>Le nom affiché est bien plus permissif que l'alias : {@code @UMLClass.name()}
     * accepte les lettres unicode, les espaces, '.', '_' et '-'. Deux écueils en découlent,
     * qu'aucun des deux PlantUML ne signale comme une erreur exploitable : un nom sans
     * aucun caractère ASCII alphanumérique (« 客户 », « ... ») donnerait un alias vide et
     * un diagramme d'erreur, et deux noms distincts peuvent se réduire au même alias
     * (« Mon.Client » et « MonClient »), auquel cas PlantUML fusionne silencieusement les
     * deux classes. D'où le repli sur un préfixe et la déduplication par suffixe.
     */
    private String aliasPour(String nom) {
        String base = nom.replaceAll("[^a-zA-Z0-9]", "");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "C" + base;
        }

        String candidat = base;
        for (int suffixe = 2; !aliasUtilises.add(candidat); suffixe++) {
            candidat = base + "_" + suffixe;
        }
        return candidat;
    }

    public String build() {
        return builder.toString();
    }
}