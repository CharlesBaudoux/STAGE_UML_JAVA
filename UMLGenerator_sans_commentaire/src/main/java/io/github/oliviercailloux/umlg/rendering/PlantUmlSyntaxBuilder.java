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

    private static final String OUVERTURE = "@startuml\n";

    private final StringBuilder builder = new StringBuilder();
    private final AliasRegistry alias = new AliasRegistry();
    private final UmlStyle style;
    private final UmlDiagram diagramme;
    private final RelationRenderer liens;
    private Map<String, String> navigation = Map.of();

    PlantUmlSyntaxBuilder(UmlStyle style) {
        this(style, UmlDiagram.defaut());
    }

    PlantUmlSyntaxBuilder(UmlStyle style, UmlDiagram diagramme) {
        this.style = checkNotNull(style);
        this.diagramme = checkNotNull(diagramme);
        this.liens = new RelationRenderer(style);
    }

    public void startDocument() {
        builder.append(OUVERTURE);
    }

    public void endDocument() {
        builder.insert(OUVERTURE.length(), enTete()
            + PlantUmlLayout.bloc(style, liens.largeurEtiquetteMax(),
                diagramme.hideEmptyMembers())
            + "\n");
        builder.append("@enduml\n");
    }

    private String enTete() {
        StringBuilder tete = new StringBuilder();

        if (diagramme.aUnTitre()) {
            tete.append("title ").append(PlantUmlEscaper.debutDeLigne(diagramme.title()))
                .append('\n');
        }
        if (diagramme.direction() == UMLFlow.LEFT_TO_RIGHT) {
            tete.append("left to right direction\n");
        }
        return tete.toString();
    }

    public PlantUmlSyntaxBuilder document(Iterable<UmlEntity> entities) {
        checkNotNull(entities);

        startDocument();
        addClasses(entities);

        ImmutableMap<String, ImmutableList<UmlRelation>> relations =
            AssociationsReciproques.de(entities);
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
        for (Map.Entry<UmlPackage, ImmutableList<UmlEntity>> groupe
            : PackageRenderer.groupes(entities)) {

            boolean groupee = groupe.getKey().estGroupee();
            if (groupee) {
                builder.append(PackageRenderer.ouverture(groupe.getKey()));
            }
            for (UmlEntity entity : groupe.getValue()) {
                addClass(entity);
            }
            if (groupee) {
                builder.append(PackageRenderer.FERMETURE);
            }
        }
        return this;
    }

    public PlantUmlSyntaxBuilder addClass(UmlEntity entity) {
        builder.append(ClassBoxRenderer.boite(entity,
            alias.enregistrer(entity.qualifiedName(), entity.name()),
            navigation.getOrDefault(entity.qualifiedName(), "")));
        return this;
    }

    public PlantUmlSyntaxBuilder navigation(Map<String, String> versLesVues) {
        this.navigation = checkNotNull(versLesVues);
        return this;
    }

    private PlantUmlSyntaxBuilder addRelations(UmlEntity entity,
        Iterable<? extends UmlRelation> relations) {
        String aliasSource = alias.aliasSource(entity.qualifiedName());

        for (UmlRelation relation : relations) {
            builder.append(liens.ligne(entity, relation, aliasSource, alias));
        }
        return this;
    }

    private PlantUmlSyntaxBuilder addNotes(UmlEntity entity) {
        String alias = this.alias.aliasSource(entity.qualifiedName());

        for (UmlNote note : entity.notes()) {
            builder.append(NoteRenderer.note(note, alias));
        }
        for (String regle : entity.constraints()) {
            builder.append(NoteRenderer.contrainte(regle, alias));
        }
        return this;
    }

    public String build() {
        return builder.toString();
    }
}
