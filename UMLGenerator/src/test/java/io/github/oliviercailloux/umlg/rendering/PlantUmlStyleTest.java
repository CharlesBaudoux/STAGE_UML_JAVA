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

// The choice of appearance: what it changes, and above all what it does not change.
//
// An appearance must touch styling only. The contract checked here is therefore twofold:
// each style really produces the rendering it announces, and both say exactly the same thing
// — same boxes, same members, same strokes. Without this second half, "changing the theme"
// could quietly change the diagram.
class PlantUmlStyleTest {

    // The colours PlantUML applies of its own accord, and which the library theme
    // replaced. Read in the rendered SVG: the only place where a colour is observable.
    private static final String GREY_PLANTUML = "#F1F1F1";
    private static final String BADGE_CLASS = "#ADD1B2";
    private static final String BANNER_UMLG = "#DDE7F0";

    private static UmlEntity box() {
        return UmlEntity.builder("com.example.Client", "Client")
            .fields(List.of(new UmlField("balance", "double", "-", false))).build();
    }

    // Two boxes joined by a named link: enough to observe the styling of the name too.
    private static UmlEntity[] pair() {
        return new UmlEntity[] {
            UmlEntity.builder("com.example.Client", "Client")
                .associations(ImmutableList.of(UmlAssociation.builder("field", "com.example.Target")
                    .targetMultiplicity("0..*").label("holds").build()))
                .build(),
            UmlEntity.builder("com.example.Target", "Target").build()};
    }

    // --- What each appearance writes in the source ---

    @Test
    void byDefault_noColourIsImposed() {
        // The default appearance must decide nothing at all: neither box colour, nor
        // of stroke. That is what makes it recognizable — an ordinary PlantUML diagram.
        String source = PlantUmlAssert.documentFor(box());

        assertFalse(source.contains("ClassBackgroundColor"), source);
        assertFalse(source.contains("ClassHeaderBackgroundColor"), source);
        assertFalse(source.contains("ClassBorderColor"), source);
        assertFalse(source.contains("ArrowColor"), source);
    }

    @Test
    void byDefault_theBadgeIsNotHidden() {
        // "hide circle" would remove the C/I/E next to the name. Leaving it is precisely what
        // makes the defect look like a hand-drawn diagram.
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
        // Three settings escape the choice. Visibilities are written "+" and "-", the
        // UML's notation, and not as coloured badges; a box without members shows no
        // an empty compartment; the spacing stays computed on the labels.
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

    // --- What the reader sees ---

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

    // --- Le name d'un link ---

    @Test
    void linkName_isColouredByTheThemeOnly() {
        // Colour is a matter of taste, so it follows the theme.
        assertFalse(PlantUmlAssert.documentFor(pair()).contains("<color:"),
            "The default appearance colours nothing.");
        assertTrue(PlantUmlAssert.documentFor(UmlStyle.UMLG, pair()).contains("<color:"),
            "The library theme colours the link names.");
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void linkName_staysItalicInBothCases(UmlStyle style) {
        // The italics are no affectation: the multiplicity sits in the same place as the
        // the link name, and nothing else lets the reader know which one names the stroke.
        // It is therefore shared by both appearances.
        assertTrue(PlantUmlAssert.documentFor(style, pair()).contains("<i>holds</i>"));
    }

    // --- The invariant: the appearance does not change what is said ---

    @Test
    void bothAppearances_sayExactlyTheSameThing() {
        // Comparison of everything that is not style: the box declarations and the
        // link lines, styling removed. They must match word for word.
        assertEquals(PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(pair())),
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(UmlStyle.UMLG, pair())));

        assertEquals(PlantUmlAssert.linkLines(PlantUmlAssert.documentFor(pair())),
            PlantUmlAssert.linkLines(PlantUmlAssert.documentFor(UmlStyle.UMLG, pair())));
    }

    @Test
    void bothAppearances_doNotProduceTheSameDocument() {
        // The counterpart of the previous test: they say the same thing, but they do not write it
        // not alike. Without this assertion, a style doing nothing would go unnoticed.
        assertNotEquals(PlantUmlAssert.documentFor(pair()),
            PlantUmlAssert.documentFor(UmlStyle.UMLG, pair()));
    }
}
