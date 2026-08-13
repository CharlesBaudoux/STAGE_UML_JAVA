package io.github.oliviercailloux.umlg.rendering.element;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ClassBoxRenderer {

    private ClassBoxRenderer() {}

    public static String boite(UmlEntity entity, String alias, String lien) {
        StringBuilder boite = new StringBuilder();

        String nomEcrit = PlantUmlEscaper.debutDeLigne(entity.name() + entity.typeParameters());
        checkArgument(!nomEcrit.isBlank(),
            "The name of %s draws nothing: it is made of invisible characters only.",
            entity.qualifiedName());

        boite.append(entity.kind().motCle())
             .append(" \"")
             .append(nomEcrit)
             .append("\" as ").append(alias);

        String stereotype =
            entity.stereotype() == null ? "" : PlantUmlEscaper.stereotype(entity.stereotype());
        if (!stereotype.isBlank()) {
            boite.append(" <<").append(stereotype).append(">>");
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

    private static String derive(boolean derived, String nom, boolean ouvreLaLigne) {
        if (derived) {
            return "/" + PlantUmlEscaper.label(nom);
        }
        return ouvreLaLigne ? PlantUmlEscaper.debutDeLigne(nom) : PlantUmlEscaper.label(nom);
    }

    private static String literaux(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (String literal : entity.literals()) {
            compartiment.append("  ").append(PlantUmlEscaper.debutDeLigne(literal)).append("\n");
        }
        return compartiment.toString();
    }

    private static String multiplicite(String multiplicity) {
        return multiplicity.isEmpty() ? "" : " [" + multiplicity + "]";
    }

    private static String attributs(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (UmlField field : entity.fields()) {
            String visibilite = prefixe(field.visibility());
            compartiment.append("  ").append(visibilite);

            if (field.isStatic()) {
                compartiment.append("{static} ");
            }

            compartiment
                .append(derive(field.derived(), field.name(),
                    visibilite.isEmpty() && !field.isStatic()))
                .append(" : ").append(PlantUmlEscaper.label(field.type()))
                .append(multiplicite(field.multiplicity()))
                .append(NotationContrainte.aLaSuite(field.constraints())).append("\n");
        }
        return compartiment.toString();
    }

    private static String methodes(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (UmlMethod method : entity.methods()) {
            String visibilite = prefixe(method.visibility());
            compartiment.append("  ").append(visibilite);

            if (method.isStatic()) {
                compartiment.append("{static} ");
            }
            if (method.isAbstract()) {
                compartiment.append("{abstract} ");
            }

            compartiment
                .append(derive(method.derived(), method.name(),
                    visibilite.isEmpty() && !method.isStatic() && !method.isAbstract()))
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
            + multiplicite(method.multiplicity());
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
