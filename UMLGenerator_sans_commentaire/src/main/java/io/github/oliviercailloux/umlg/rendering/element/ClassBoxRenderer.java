package io.github.oliviercailloux.umlg.rendering.element;

import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ClassBoxRenderer {

    private ClassBoxRenderer() {}

    public static String boite(UmlEntity entity, String alias) {
        return boite(entity, alias, "");
    }

    public static String boite(UmlEntity entity, String alias, String lien) {
        StringBuilder boite = new StringBuilder();

        boite.append(entity.kind().motCle())
             .append(" \"")
             .append(PlantUmlEscaper.label(entity.name() + entity.typeParameters()))
             .append("\" as ").append(alias);

        if (entity.stereotype() != null && !entity.stereotype().isBlank()) {
            boite.append(" <<").append(PlantUmlEscaper.stereotype(entity.stereotype()))
                 .append(">>");
        }

        if (!lien.isEmpty()) {
            boite.append(" [[").append(lien).append("]]");
        }

        if (entity.color() != null && !entity.color().isBlank()) {
            boite.append(" ").append(ColorCodec.code(entity.color(), entity.qualifiedName()));
        }

        boite.append(" {\n");

        boite.append(Stream.of(literaux(entity), attributs(entity), methodes(entity))
                           .filter(compartiment -> !compartiment.isEmpty())
                           .collect(Collectors.joining("  --\n")));

        return boite.append("}\n\n").toString();
    }

    private static String prefixe(String visibilite) {
        return visibilite.isEmpty() ? "" : PlantUmlEscaper.label(visibilite) + " ";
    }

    private static String contraintes(List<String> regles) {
        StringBuilder sortie = new StringBuilder();
        for (String regle : regles) {
            sortie.append(" {").append(PlantUmlEscaper.label(regle)).append("}");
        }
        return sortie.toString();
    }

    private static String derive(boolean derived, String nom) {
        return (derived ? "/" : "") + PlantUmlEscaper.label(nom);
    }

    private static String literaux(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (String literal : entity.literals()) {
            compartiment.append("  ").append(PlantUmlEscaper.label(literal)).append("\n");
        }
        return compartiment.toString();
    }

    private static String multiplicite(String cardinality) {
        return cardinality.isEmpty() ? "" : " [" + cardinality + "]";
    }

    private static String attributs(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (UmlField field : entity.fields()) {
            compartiment.append("  ").append(prefixe(field.visibility()));

            if (field.isStatic()) {
                compartiment.append("{static} ");
            }

            compartiment.append(derive(field.derived(), field.name()))
                        .append(" : ").append(PlantUmlEscaper.label(field.type()))
                        .append(multiplicite(field.cardinality()))
                        .append(contraintes(field.constraints())).append("\n");
        }
        return compartiment.toString();
    }

    private static String methodes(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (UmlMethod method : entity.methods()) {
            compartiment.append("  ").append(prefixe(method.visibility()));

            if (method.isStatic()) {
                compartiment.append("{static} ");
            }
            if (method.isAbstract()) {
                compartiment.append("{abstract} ");
            }

            compartiment.append(derive(method.derived(), method.name()))
                        .append("(").append(parametres(method)).append(")")
                        .append(retour(method))
                        .append("\n");
        }
        return compartiment.toString();
    }

    private static String retour(UmlMethod method) {
        if (method.returnType().isEmpty()) {
            return "";
        }
        return " : " + PlantUmlEscaper.label(method.returnType())
            + multiplicite(method.cardinality());
    }

    private static String parametres(UmlMethod method) {
        return method.parameters().stream()
                     .map(parametre -> parametre.nomme()
                         ? PlantUmlEscaper.label(parametre.name()) + " : "
                             + PlantUmlEscaper.label(parametre.type())
                         : PlantUmlEscaper.label(parametre.type()))
                     .collect(Collectors.joining(", "));
    }
}
