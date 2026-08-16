package io.github.oliviercailloux.umlg.rendering;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlNote;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import io.github.oliviercailloux.umlg.rendering.element.ClassBoxRenderer;
import io.github.oliviercailloux.umlg.rendering.element.NoteRenderer;
import io.github.oliviercailloux.umlg.rendering.element.PackageRenderer;
import io.github.oliviercailloux.umlg.rendering.element.RelationRenderer;
import io.github.oliviercailloux.umlg.rendering.syntax.AliasRegistry;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlLayout;
import java.util.Map;

final class PlantUmlSyntaxBuilder {
    private static final String Opening = "@startuml\n";

    private final StringBuilder builder = new StringBuilder();

    private final AliasRegistry alias = new AliasRegistry();

    private final UmlStyle style;

    private final UmlDiagram diagram;
    private final RelationRenderer links;

    private Map<String, String> navigation = Map.of();

    PlantUmlSyntaxBuilder(UmlStyle style) {
        this(style, UmlDiagram.defaults());
    }

    PlantUmlSyntaxBuilder(UmlStyle style, UmlDiagram diagram) {
        this.style = checkNotNull(style);
        this.diagram = checkNotNull(diagram);
        this.links = new RelationRenderer(style);
    }

    public void startDocument() {
        builder.append(Opening);
    }

    public void endDocument() {
        builder.insert(Opening.length(), header()
            + PlantUmlLayout.bloc(style, links.widthLabelMax(),
                diagram.hideEmptyMembers())
            + "\n");
        builder.append("@enduml\n");
    }

    private String header() {
        StringBuilder head = new StringBuilder();

        if (diagram.hasTitle()) {
            head.append("title ").append(PlantUmlEscaper.lineStart(diagram.title()))
                .append('\n');
        }
        if (diagram.direction() == UMLFlow.LEFT_TO_RIGHT) {
            head.append("left to right direction\n");
        }
        return head.toString();
    }

    public PlantUmlSyntaxBuilder document(Iterable<UmlEntity> entities) {
        checkNotNull(entities);

        startDocument();
        addClasses(entities);

        ImmutableMap<String, ImmutableList<UmlRelation>> relations =
            ReciprocalAssociations.of(entities);
        for (UmlEntity entity : entities) {
            addRelations(entity, relations.get(entity.qualifiedName()));
        }
        for (UmlEntity entity : entities) {
            addNotes(entity);
        }
        endDocument();
        return this;
    }

    private PlantUmlSyntaxBuilder addClasses(Iterable<UmlEntity> entities) {
        for (Map.Entry<UmlPackage, ImmutableList<UmlEntity>> group
            : PackageRenderer.groups(entities)) {
            boolean grouped = group.getKey().isGrouped();
            if (grouped) {
                builder.append(PackageRenderer.opening(group.getKey()));
            }
            for (UmlEntity entity : group.getValue()) {
                addClass(entity);
            }
            if (grouped) {
                builder.append(PackageRenderer.Closing);
            }
        }
        return this;
    }

    public PlantUmlSyntaxBuilder addClass(UmlEntity entity) {
        builder.append(ClassBoxRenderer.box(entity,
            alias.register(entity.qualifiedName(), entity.name()),
            navigation.getOrDefault(entity.qualifiedName(), "")));
        return this;
    }

    public PlantUmlSyntaxBuilder navigation(Map<String, String> toViews) {
        this.navigation = checkNotNull(toViews);
        return this;
    }

    private PlantUmlSyntaxBuilder addRelations(UmlEntity entity,
        Iterable<? extends UmlRelation> relations) {
        String aliasSource = alias.aliasSource(entity.qualifiedName());

        for (UmlRelation relation : relations) {
            builder.append(links.line(entity, relation, aliasSource, alias));
        }
        return this;
    }

    private PlantUmlSyntaxBuilder addNotes(UmlEntity entity) {
        String alias = this.alias.aliasSource(entity.qualifiedName());

        for (UmlNote note : entity.notes()) {
            builder.append(NoteRenderer.note(note, alias));
        }

        for (String rule : entity.constraints()) {
            builder.append(NoteRenderer.constraint(rule, alias));
        }
        return this;
    }

    public String build() {
        return builder.toString();
    }
}
