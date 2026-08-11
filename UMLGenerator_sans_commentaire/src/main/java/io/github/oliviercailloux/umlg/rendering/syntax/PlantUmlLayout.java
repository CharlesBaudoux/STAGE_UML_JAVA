package io.github.oliviercailloux.umlg.rendering.syntax;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.UmlStyle;

public final class PlantUmlLayout {

    private static final int LARGEUR_CARACTERE = 7;

    private static final int SEPARATION_MINIMALE = 60;

    private static final int SEPARATION_MAXIMALE = 220;

    private static final int SEPARATION_RANGS = 90;

    private static final String COMMUN = """
        skinparam classAttributeIconSize 0
        """;

    private static final String MASQUER_MEMBRES_VIDES = "hide empty members\n";

    private static final String THEME_UMLG = """
        skinparam shadowing false
        skinparam roundCorner 8
        skinparam defaultFontName SansSerif
        skinparam defaultFontSize 13
        skinparam ArrowFontSize 12
        skinparam ArrowThickness 1.1
        skinparam ArrowColor #5C7A96
        skinparam ArrowFontColor #1A1A1A
        skinparam ClassBorderColor #33526E
        skinparam ClassBackgroundColor #FDFDFE
        skinparam ClassHeaderBackgroundColor #DDE7F0
        skinparam ClassFontStyle bold
        skinparam ClassFontSize 15
        skinparam ClassStereotypeFontSize 11
        hide circle
        """;

    private static final String COULEUR_NOM_DE_LIEN = "#3C5F82";

    private PlantUmlLayout() {}

    public static String nomDeLien(UmlStyle style, String labelDejaEchappe) {
        checkNotNull(style);

        String italique = "<i>" + labelDejaEchappe + "</i>";

        return style == UmlStyle.UMLG
            ? "<color:" + COULEUR_NOM_DE_LIEN + ">" + italique + "</color>"
            : italique;
    }

    public static int largeurEstimee(String etiquette) {
        return etiquette.length() * LARGEUR_CARACTERE;
    }

    public static String bloc(UmlStyle style, int largeurEtiquetteMax,
        boolean masquerMembresVides) {
        checkNotNull(style);

        return COMMUN
            + (masquerMembresVides ? MASQUER_MEMBRES_VIDES : "")
            + (style == UmlStyle.UMLG ? THEME_UMLG : "")
            + "skinparam nodesep " + separationEntreVoisins(largeurEtiquetteMax) + "\n"
            + "skinparam ranksep " + SEPARATION_RANGS + "\n";
    }

    private static int separationEntreVoisins(int largeurEtiquetteMax) {
        return Math.clamp(largeurEtiquetteMax, SEPARATION_MINIMALE, SEPARATION_MAXIMALE);
    }
}
