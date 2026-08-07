package io.github.oliviercailloux.umlg.rendering.element;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import io.github.oliviercailloux.umlg.rendering.syntax.AliasRegistry;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlLayout;
import java.util.Locale;

// Écrit les lignes de relation du fichier PlantUML.
//
// Forme produite, pour une association :
//   Client "1" *-down- "0..*" Commande : <i>passe</i>
//   ^ source  ^extrémité ^connecteur ^extrémité ^cible   ^label
//
// Cette classe décide de la forme du trait — flèche, losange, pointillé — et de l'ordre de ses
// morceaux ; le texte des extrémités est composé par Extremite.
//
// Contrairement aux autres renderers, elle s'instancie : elle retient au passage la largeur de
// la plus grande étiquette, dont PlantUmlLayout se sert pour calculer l'espacement du diagramme.
// Une instance par diagramme, donc.
public final class RelationRenderer {

    private final UmlStyle style;

    private int largeurEtiquetteMax;

    public RelationRenderer(UmlStyle style) {
        this.style = checkNotNull(style);
    }

    public String ligne(UmlEntity source, UmlRelation relation, String aliasSource,
        AliasRegistry registre) {

        String aliasCible =
            registre.aliasCible(relation.targetQualifiedName(), designation(source, relation));

        String trait = switch (relation) {
            case UmlAssociation association -> trait(association, aliasSource, aliasCible);
            case UmlDependency dependance -> trait(dependance, aliasSource, aliasCible);
            case UmlInheritance heritage -> trait(heritage, aliasSource, aliasCible);
        };

        if (!relation.label().isEmpty()) {
            trait +=
                " : " + PlantUmlLayout.nomDeLien(style, PlantUmlEscaper.label(relation.label()));
        }
        return trait + "\n";
    }

    public int largeurEtiquetteMax() {
        return largeurEtiquetteMax;
    }

    private static String designation(UmlEntity source, UmlRelation relation) {
        return switch (relation) {
            case UmlAssociation association ->
                "The association carried by " + source.qualifiedName() + "."
                    + association.fieldName();
            case UmlDependency ignore -> "The dependency declared by " + source.qualifiedName();
            case UmlInheritance heritage -> switch (heritage.type()) {
                case GENERALIZATION ->
                    "The generalization declared by " + source.qualifiedName();
                case REALIZATION -> "The realization declared by " + source.qualifiedName();
            };
        };
    }

    private String trait(UmlDependency dependance, String aliasSource, String aliasCible) {
        return aliasSource + " ..> " + aliasCible;
    }

    private String trait(UmlInheritance heritage, String aliasSource, String aliasCible) {
        String connecteur = switch (heritage.type()) {
            case GENERALIZATION -> " <|-- ";
            case REALIZATION -> " <|.. ";
        };
        return aliasCible + connecteur + aliasSource;
    }

    private String trait(UmlAssociation association, String aliasSource, String aliasCible) {
        StringBuilder ligne = new StringBuilder(aliasSource);
        ajouterExtremite(ligne, Extremite.source(association));
        ligne.append(' ').append(connecteur(association));
        ajouterExtremite(ligne, Extremite.cible(association));
        return ligne.append(' ').append(aliasCible).toString();
    }

    private void ajouterExtremite(StringBuilder ligne, String extremite) {
        if (extremite.isEmpty()) {
            return;
        }
        ligne.append(" \"").append(extremite).append('"');
        largeurEtiquetteMax =
            Math.max(largeurEtiquetteMax, PlantUmlLayout.largeurEstimee(extremite));
    }

    private static String connecteur(UmlAssociation association) {
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
