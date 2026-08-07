package io.github.oliviercailloux.umlg.rendering;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlNote;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import io.github.oliviercailloux.umlg.rendering.element.ClassBoxRenderer;
import io.github.oliviercailloux.umlg.rendering.element.NoteRenderer;
import io.github.oliviercailloux.umlg.rendering.element.RelationRenderer;
import io.github.oliviercailloux.umlg.rendering.syntax.AliasRegistry;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlLayout;

// Assemble le texte PlantUML complet, morceau par morceau.
//
// Il ne met en forme rien lui-même : les boîtes viennent de ClassBoxRenderer, les traits de
// RelationRenderer, le style de PlantUmlLayout. Son rôle est de tenir l'état commun à un
// diagramme — le registre d'alias, l'accumulateur de largeur — et de placer les morceaux dans
// le bon ordre.
final class PlantUmlSyntaxBuilder {

    private static final String OUVERTURE = "@startuml\n";

    private final StringBuilder builder = new StringBuilder();
    private final AliasRegistry alias = new AliasRegistry();
    private final UmlStyle style;
    private final RelationRenderer liens;

    PlantUmlSyntaxBuilder(UmlStyle style) {
        this.style = checkNotNull(style);
        this.liens = new RelationRenderer(style);
    }

    public void startDocument() {
        builder.append(OUVERTURE);
    }

    public void endDocument() {
        builder.insert(OUVERTURE.length(),
            PlantUmlLayout.bloc(style, liens.largeurEtiquetteMax()) + "\n");
        builder.append("@enduml\n");
    }

    public PlantUmlSyntaxBuilder addClass(UmlEntity entity) {
        builder.append(ClassBoxRenderer.boite(entity,
            alias.enregistrer(entity.qualifiedName(), entity.name())));
        return this;
    }

    public PlantUmlSyntaxBuilder addRelations(UmlEntity entity) {
        String aliasSource = alias.aliasSource(entity.qualifiedName());

        for (UmlRelation relation : entity.relations()) {
            builder.append(liens.ligne(entity, relation, aliasSource, alias));
        }
        return this;
    }

    public PlantUmlSyntaxBuilder addNotes(UmlEntity entity) {
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
