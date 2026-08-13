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
            case UmlLayout placement -> trait(placement, aliasSource, aliasCible);
            case UmlAssociationClass classe -> trait(classe, aliasSource, aliasCible,
                registre.aliasCible(classe.sourceQualifiedName(), designation(source, relation)));
        };

        String nom = nomDeLien(relation.label());
        if (!nom.isEmpty()) {
            trait += " : " + nom;
            mesurer(relation.label());
        }
        return trait + "\n";
    }

    private String nomDeLien(String label) {
        String[] lignes = label.split("\r\n|\r|\n", -1);

        StringBuilder sortie = new StringBuilder();
        boolean quelqueChose = false;
        for (int i = 0; i < lignes.length; i++) {
            if (i > 0) {
                sortie.append("\\n");
            }
            String echappee = PlantUmlEscaper.label(lignes[i]);
            if (!echappee.isEmpty()) {
                sortie.append(PlantUmlLayout.nomDeLien(style, echappee));
                quelqueChose = true;
            }
        }
        return quelqueChose ? sortie.toString() : "";
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
            case UmlLayout ignore -> "The layout declared by " + source.qualifiedName();
            case UmlAssociationClass ignore ->
                "The association class " + source.qualifiedName();
        };
    }

    private String trait(UmlLayout placement, String aliasSource, String aliasCible) {
        String direction = placement.direction() == UMLDirection.NONE
            ? ""
            : placement.direction().name().toLowerCase(Locale.ROOT);

        return aliasSource + " -[hidden]" + direction + "-> " + aliasCible;
    }

    private String trait(UmlAssociationClass ignore, String aliasPorteur, String aliasCible,
        String aliasDepart) {
        return "(" + aliasDepart + ", " + aliasCible + ") .. " + aliasPorteur;
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

    private void ajouterExtremite(StringBuilder ligne, Extremite.Etiquette extremite) {
        if (extremite.estVide()) {
            return;
        }
        ligne.append(" \"").append(extremite.ecrit()).append('"');
        mesurer(extremite.affiche());
    }

    private void mesurer(String texteAffiche) {
        for (String ligne : texteAffiche.split("\r\n|\r|\n", -1)) {
            largeurEtiquetteMax =
                Math.max(largeurEtiquetteMax, PlantUmlLayout.largeurEstimee(ligne));
        }
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
