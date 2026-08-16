package io.github.oliviercailloux.umlg.rendering.element;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import io.github.oliviercailloux.umlg.model.UmlLayout;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import io.github.oliviercailloux.umlg.rendering.syntax.AliasRegistry;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlLayout;
import java.util.Locale;

public final class RelationRenderer {
    private final UmlStyle style;

    private int widthLabelMax;

    public RelationRenderer(UmlStyle style) {
        this.style = checkNotNull(style);
    }

    public String line(UmlEntity source, UmlRelation relation, String aliasSource,
        AliasRegistry registry) {
        String aliasTarget =
            registry.aliasTarget(relation.targetQualifiedName(), designation(source, relation));

        String stroke = switch (relation) {
            case UmlAssociation association -> stroke(association, aliasSource, aliasTarget);
            case UmlDependency dependency -> stroke(dependency, aliasSource, aliasTarget);
            case UmlInheritance inheritance -> stroke(inheritance, aliasSource, aliasTarget);
            case UmlLayout placement -> stroke(placement, aliasSource, aliasTarget);

            case UmlAssociationClass clazz -> stroke(clazz, aliasSource, aliasTarget,
                registry.aliasTarget(clazz.sourceQualifiedName(), designation(source, relation)));
        };

        String name = linkName(relation.label());
        if (!name.isEmpty()) {
            stroke += " : " + name;
            measure(relation.label());
        }
        return stroke + "\n";
    }

    private String linkName(String label) {
        String[] lines = label.split("\r\n|\r|\n", -1);

        StringBuilder output = new StringBuilder();
        boolean someThing = false;
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                output.append("\\n");
            }
            String escaped = PlantUmlEscaper.label(lines[i]);
            if (!escaped.isEmpty()) {
                output.append(PlantUmlLayout.linkName(style, escaped));
                someThing = true;
            }
        }
        return someThing ? output.toString() : "";
    }

    public int widthLabelMax() {
        return widthLabelMax;
    }

    private static String designation(UmlEntity source, UmlRelation relation) {
        return switch (relation) {
            case UmlAssociation association ->
                "The association carried by " + source.qualifiedName() + "."
                    + association.fieldName();

            case UmlDependency ignore -> "The dependency declared by " + source.qualifiedName();
            case UmlInheritance inheritance -> switch (inheritance.type()) {
                case GENERALIZATION ->
                    "The generalization declared by " + source.qualifiedName();
                case REALIZATION -> "The realization declared by " + source.qualifiedName();
            };
            case UmlLayout ignore -> "The layout declared by " + source.qualifiedName();
            case UmlAssociationClass ignore ->
                "The association class " + source.qualifiedName();
        };
    }

    private String stroke(UmlLayout placement, String aliasSource, String aliasTarget) {
        String direction = placement.direction() == UMLDirection.NONE
            ? ""
            : placement.direction().name().toLowerCase(Locale.ROOT);

        return aliasSource + " -[hidden]" + direction + "-> " + aliasTarget;
    }

    private String stroke(UmlAssociationClass ignore, String aliasHolder, String aliasTarget,
        String aliasStart) {
        return "(" + aliasStart + ", " + aliasTarget + ") .. " + aliasHolder;
    }

    private String stroke(UmlDependency dependency, String aliasSource, String aliasTarget) {
        return aliasSource + " ..> " + aliasTarget;
    }

    private String stroke(UmlInheritance inheritance, String aliasSource, String aliasTarget) {
        String connector = switch (inheritance.type()) {
            case GENERALIZATION -> " <|-- ";
            case REALIZATION -> " <|.. ";
        };
        return aliasTarget + connector + aliasSource;
    }

    private String stroke(UmlAssociation association, String aliasSource, String aliasTarget) {
        StringBuilder line = new StringBuilder(aliasSource);
        addEndpoint(line, Endpoint.source(association));
        line.append(' ').append(connector(association));
        addEndpoint(line, Endpoint.target(association));
        return line.append(' ').append(aliasTarget).toString();
    }

    private void addEndpoint(StringBuilder line, Endpoint.Label endpoint) {
        if (endpoint.isEmpty()) {
            return;
        }
        line.append(" \"").append(endpoint.written()).append('"');
        measure(endpoint.displayed());
    }

    private void measure(String texteAffiche) {
        for (String line : texteAffiche.split("\r\n|\r|\n", -1)) {
            widthLabelMax =
                Math.max(widthLabelMax, PlantUmlLayout.widthEstimated(line));
        }
    }

    private static String connector(UmlAssociation association) {
        String direction = association.direction() == UMLDirection.NONE
            ? ""
            : association.direction().name().toLowerCase(Locale.ROOT);

        return switch (association.type()) {
            case ASSOCIATION -> "-" + direction + "->";
            case AGGREGATION -> "o-" + direction + "-";
            case COMPOSITION -> "*-" + direction + "-";
        };
    }
}
