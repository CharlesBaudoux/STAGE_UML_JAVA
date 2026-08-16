package io.github.oliviercailloux.umlg.rendering.syntax;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.UmlStyle;

public final class PlantUmlLayout {
    private static final int WIDTH_CHARACTER = 7;

    private static final int SEPARATION_MINIMUM = 60;

    private static final int SEPARATION_MAXIMUM = 220;

    private static final int SEPARATION_RANKS = 90;

    private static final String Shared = """
        skinparam classAttributeIconSize 0
        """;

    private static final String HIDE_EMPTY_MEMBERS = "hide empty members\n";

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

    private static final String LINK_COLOR_NAME = "#3C5F82";

    private PlantUmlLayout() {}

    public static String linkName(UmlStyle style, String labelEscapedAlready) {
        checkNotNull(style);

        String italic = "<i>" + labelEscapedAlready + "</i>";

        return style == UmlStyle.UMLG
            ? "<color:" + LINK_COLOR_NAME + ">" + italic + "</color>"
            : italic;
    }

    public static int widthEstimated(String label) {
        return label.length() * WIDTH_CHARACTER;
    }

    public static String bloc(UmlStyle style, int widthLabelMax,
        boolean hideEmptyMembers) {
        checkNotNull(style);

        return Shared
            + (hideEmptyMembers ? HIDE_EMPTY_MEMBERS : "")
            + (style == UmlStyle.UMLG ? THEME_UMLG : "")
            + "skinparam nodesep " + separationBetweenNeighbors(widthLabelMax) + "\n"
            + "skinparam ranksep " + SEPARATION_RANKS + "\n";
    }

    private static int separationBetweenNeighbors(int widthLabelMax) {
        return Math.clamp(widthLabelMax, SEPARATION_MINIMUM, SEPARATION_MAXIMUM);
    }
}
