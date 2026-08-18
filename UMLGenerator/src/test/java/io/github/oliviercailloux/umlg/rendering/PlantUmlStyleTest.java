package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PlantUmlStyleTest {

    private static final String GREY_PLANTUML = "#F1F1F1";
    private static final String BADGE_CLASS = "#ADD1B2";
    private static final String BANNER_UMLG = "#DDE7F0";

    private static UmlEntity box() {
        return UmlEntity.builder("com.example.Client", "Client")
            .fields(List.of(new UmlField("balance", "double", "-", false))).build();
    }

    private static UmlEntity[] pair() {
        return new UmlEntity[] {
            UmlEntity.builder("com.example.Client", "Client")
                .associations(ImmutableList.of(UmlAssociation.builder("field", "com.example.Target")
                    .targetMultiplicity("0..*").label("holds").build()))
                .build(),
            UmlEntity.builder("com.example.Target", "Target").build()};
    }

    @Test
    void byDefault_noColourIsImposed() {
        String source = PlantUmlAssert.documentFor(box());

        assertFalse(source.contains("ClassBackgroundColor"), source);
        assertFalse(source.contains("ClassHeaderBackgroundColor"), source);
        assertFalse(source.contains("ClassBorderColor"), source);
        assertFalse(source.contains("ArrowColor"), source);
    }

    @Test
    void byDefault_theBadgeIsNotHidden() {
        assertFalse(PlantUmlAssert.documentFor(box()).contains("hide circle"));
    }

    @Test
    void umlgTheme_imposesItsColoursAndHidesTheBadge() {
        String source = PlantUmlAssert.documentFor(UmlStyle.UMLG, box());

        assertTrue(source.contains("ClassHeaderBackgroundColor " + BANNER_UMLG), source);
        assertTrue(source.contains("hide circle"), source);
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void everyAppearance_keepsWhatBelongsToMeaning(UmlStyle style) {
        String source = PlantUmlAssert.documentFor(style, box());

        assertTrue(source.contains("skinparam classAttributeIconSize 0"), source);
        assertTrue(source.contains("hide empty members"), source);
        assertTrue(source.contains("skinparam nodesep "), source);
        assertTrue(source.contains("skinparam ranksep "), source);
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void everyAppearance_isAcceptedByPlantUml(UmlStyle style) {
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentFor(style, pair()));
    }

    @Test
    void byDefault_theRenderingIsPlantUmlOwn() throws Exception {
        String svg = PlantUmlAssert.svg(box());

        assertTrue(svg.contains(GREY_PLANTUML),
            () -> "PlantUML's grey background is expected:\n" + svg);
        assertTrue(svg.contains(BADGE_CLASS),
            () -> "The green \u00ab C \u00bb badge is expected:\n" + svg);
    }

    @Test
    void umlgTheme_replacesThePlantUmlRendering() throws Exception {
        String svg = PlantUmlAssert.svg(UmlStyle.UMLG, box());

        assertTrue(svg.contains(BANNER_UMLG), () -> "The blue banner is expected:\n" + svg);
        assertFalse(svg.contains(BADGE_CLASS),
            () -> "The badge must be hidden under this theme:\n" + svg);
    }

    @Test
    void linkName_isColouredByTheThemeOnly() {
        assertFalse(PlantUmlAssert.documentFor(pair()).contains("<color:"),
            "The default appearance colours nothing.");
        assertTrue(PlantUmlAssert.documentFor(UmlStyle.UMLG, pair()).contains("<color:"),
            "The library theme colours the link names.");
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void linkName_staysItalicInBothCases(UmlStyle style) {
        assertTrue(PlantUmlAssert.documentFor(style, pair()).contains("<i>holds</i>"));
    }

    @Test
    void bothAppearances_sayExactlyTheSameThing() {
        assertEquals(PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(pair())),
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(UmlStyle.UMLG, pair())));

        assertEquals(PlantUmlAssert.linkLines(PlantUmlAssert.documentFor(pair())),
            PlantUmlAssert.linkLines(PlantUmlAssert.documentFor(UmlStyle.UMLG, pair())));
    }

    @Test
    void bothAppearances_doNotProduceTheSameDocument() {
        assertNotEquals(PlantUmlAssert.documentFor(pair()),
            PlantUmlAssert.documentFor(UmlStyle.UMLG, pair()));
    }
}
