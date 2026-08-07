package io.github.oliviercailloux.umlg.rendering.element;

import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.rendering.syntax.AliasRegistry;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Écrit la déclaration PlantUML d'une boîte.
//
// Forme produite :
//   class "Compte" as Compte <<entity>> #LightBlue {
//     - double solde
//     --
//     + void retirer(double)
//   }
//
// Le mot-clé initial dépend de la nature de l'entité, et une énumération ouvre la boîte sur un
// compartiment supplémentaire, celui de ses littéraux :
//   enum "Devise" as Devise {
//     EURO
//     DOLLAR
//   }
//
// Tout texte venu du modèle passe par PlantUmlEscaper : c'est le seul rempart contre un nom
// contenant un guillemet, un chevron ou un retour à la ligne.
public final class ClassBoxRenderer {

    private ClassBoxRenderer() {}

    public static String boite(UmlEntity entity, String alias) {
        StringBuilder boite = new StringBuilder();

        boite.append(entity.kind().motCle())
             .append(" \"").append(PlantUmlEscaper.label(entity.name()))
             .append("\" as ").append(alias);

        if (entity.stereotype() != null && !entity.stereotype().isBlank()) {
            boite.append(" <<").append(PlantUmlEscaper.stereotype(entity.stereotype()))
                 .append(">>");
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

    private static String attributs(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (UmlField field : entity.fields()) {
            compartiment.append("  ").append(PlantUmlEscaper.label(field.visibility())).append(" ");

            if (field.isStatic()) {
                compartiment.append("{static} ");
            }

            compartiment.append(PlantUmlEscaper.label(field.type())).append(" ")
                        .append(derive(field.derived(), field.name()))
                        .append(contraintes(field.constraints())).append("\n");
        }
        return compartiment.toString();
    }

    private static String methodes(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (UmlMethod method : entity.methods()) {
            compartiment.append("  ").append(PlantUmlEscaper.label(method.visibility()))
                        .append(" ");

            if (method.isStatic()) {
                compartiment.append("{static} ");
            }

            compartiment.append(PlantUmlEscaper.label(method.returnType())).append(" ")
                        .append(derive(method.derived(), method.name())).append("(");
            compartiment.append(String.join(", ",
                method.parameterTypes().stream().map(PlantUmlEscaper::label).toList()));
            compartiment.append(")\n");
        }
        return compartiment.toString();
    }
}
