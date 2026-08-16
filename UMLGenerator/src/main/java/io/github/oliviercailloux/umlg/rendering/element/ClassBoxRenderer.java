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

    public static String box(UmlEntity entity, String alias, String link) {
        StringBuilder box = new StringBuilder();

        String nameWritten = PlantUmlEscaper.lineStart(entity.name() + entity.typeParameters());
        checkArgument(!nameWritten.isBlank(),
            "The name of %s draws nothing: it is made of invisible characters only.",
            entity.qualifiedName());

        box.append(entity.kind().keyword())
             .append(" \"")
             .append(nameWritten)
             .append("\" as ").append(alias);

        String stereotype =
            entity.stereotype() == null ? "" : PlantUmlEscaper.stereotype(entity.stereotype());
        if (!stereotype.isBlank()) {
            box.append(" <<").append(stereotype).append(">>");
        }

        if (!link.isEmpty()) {
            box.append(" [[").append(link).append("]]");
        }

        if (entity.color() != null && !entity.color().isBlank()) {
            box.append(" ").append(ColorCodec.code(entity.color(), entity.qualifiedName()));
        }

        box.append(" {\n");

        box.append(Stream.of(literals(entity), attributes(entity), methods(entity))
                           .filter(compartment -> !compartment.isEmpty())
                           .collect(Collectors.joining("  --\n")));

        return box.append("}\n\n").toString();
    }

    private static String prefixe(String visibility) {
        return visibility.isEmpty() ? "" : PlantUmlEscaper.label(visibility) + " ";
    }

    private static String derived(boolean derived, String name, boolean opensLine) {
        if (derived) {
            return "/" + PlantUmlEscaper.label(name);
        }
        return opensLine ? PlantUmlEscaper.lineStart(name) : PlantUmlEscaper.label(name);
    }

    private static String literals(UmlEntity entity) {
        StringBuilder compartment = new StringBuilder();
        for (String literal : entity.literals()) {
            compartment.append("  ").append(PlantUmlEscaper.lineStart(literal)).append("\n");
        }
        return compartment.toString();
    }

    private static String multiplicity(String multiplicity) {
        return multiplicity.isEmpty() ? "" : " [" + multiplicity + "]";
    }

    private static String attributes(UmlEntity entity) {
        StringBuilder compartment = new StringBuilder();
        for (UmlField field : entity.fields()) {
            String visibility = prefixe(field.visibility());
            compartment.append("  ").append(visibility);

            if (field.isStatic()) {
                compartment.append("{static} ");
            }

            compartment
                .append(derived(field.derived(), field.name(),
                    visibility.isEmpty() && !field.isStatic()))
                .append(" : ").append(PlantUmlEscaper.label(field.type()))
                .append(multiplicity(field.multiplicity()))
                .append(ConstraintNotation.aSuite(field.constraints())).append("\n");
        }
        return compartment.toString();
    }

    private static String methods(UmlEntity entity) {
        StringBuilder compartment = new StringBuilder();
        for (UmlMethod method : entity.methods()) {
            String visibility = prefixe(method.visibility());
            compartment.append("  ").append(visibility);

            if (method.isStatic()) {
                compartment.append("{static} ");
            }

            if (method.isAbstract()) {
                compartment.append("{abstract} ");
            }

            compartment
                .append(derived(method.derived(), method.name(),
                    visibility.isEmpty() && !method.isStatic() && !method.isAbstract()))
                .append("(").append(parameters(method)).append(")")
                .append(returnType(method))
                .append("\n");
        }
        return compartment.toString();
    }

    private static String returnType(UmlMethod method) {
        if (method.returnType().isEmpty()) {
            return "";
        }
        return " : " + PlantUmlEscaper.label(method.returnType())
            + multiplicity(method.multiplicity());
    }

    private static String parameters(UmlMethod method) {
        return method.parameters().stream()
                     .map(parameter -> parameter.isNamed()
                         ? PlantUmlEscaper.label(parameter.name()) + " : "
                             + PlantUmlEscaper.label(parameter.type())
                         : PlantUmlEscaper.label(parameter.type()))
                     .collect(Collectors.joining(", "));
    }
}
