package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PlantUmlLinkNameTest {

    private static final Pattern NODESEP = Pattern.compile("skinparam nodesep (\\d+)");

    private static UmlEntity[] pair(UmlAssociation association) {
        return new UmlEntity[] {
            UmlEntity.builder("Source", "Source")
                .associations(ImmutableList.of(association)).build(),
            UmlEntity.builder(association.targetQualifiedName(), "Target").build()};
    }

    private static int nodesep(String source) {
        Matcher m = NODESEP.matcher(source);
        assertTrue(m.find(), () -> "No separation declared:\n" + source);
        return Integer.parseInt(m.group(1));
    }

    private static String strokeLine(String source) {
        return source.lines()
            .filter(l -> l.startsWith("Source "))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No stroke:\n" + source));
    }

    @Test
    void twoLineLinkName_stylesEachLineSeparately() {
        String source = PlantUmlAssert.documentFor(UmlStyle.UMLG,
            pair(UmlAssociation.builder("field", "Target").label("lig1\nlig2").build()));

        String stroke = strokeLine(source);

        assertTrue(stroke.contains("<i>lig1</i>"), stroke);
        assertTrue(stroke.contains("<i>lig2</i>"), stroke);
        assertFalse(stroke.contains("lig1\\nlig2"),
            "The two lines must no longer share a single styling: " + stroke);
        assertTrue(stroke.contains("</i></color>\\n"),
            "The line break stays, between two complete stylings: " + stroke);
    }

    @Test
    void twoLineLinkName_doesNotDrawItsTags() throws Exception {
        String svg = PlantUmlAssert.svg(UmlStyle.UMLG,
            pair(UmlAssociation.builder("field", "Target").label("lig1\nlig2").build()));

        assertFalse(svg.contains("&lt;/i&gt;"), "A closing tag is drawn:\n" + svg);
        assertFalse(svg.contains("&lt;/color&gt;"), "A closing tag is drawn:\n" + svg);
    }

    @Test
    void singleLineLinkName_isStyledAsBefore() {
        String stroke = strokeLine(PlantUmlAssert.documentFor(UmlStyle.UMLG,
            pair(UmlAssociation.builder("field", "Target").label("holds").build())));

        assertTrue(stroke.endsWith(" : <color:#3C5F82><i>holds</i></color>"), stroke);
    }

    @Test
    void linkNameMadeOfInvisibleCharacters_writesNoStyling() {
        String stroke = strokeLine(PlantUmlAssert.documentFor(UmlStyle.UMLG,
            pair(UmlAssociation.builder("field", "Target").label("\1\2").build())));

        assertFalse(stroke.contains("<i>"), "An empty styling stays drawn: " + stroke);
        assertFalse(stroke.contains(" : "), "The stroke must carry no name: " + stroke);
    }

    @Test
    void emptyLineInTheMiddleOfAName_receivesNoStyling() {
        String stroke = strokeLine(PlantUmlAssert.documentFor(UmlStyle.PLANTUML,
            pair(UmlAssociation.builder("field", "Target").label("top\n\nbottom").build())));

        assertTrue(stroke.endsWith(" : <i>top</i>\\n\\n<i>bottom</i>"), stroke);
    }

    @Test
    void longLinkName_pushesTheBoxesApart() {
        String shortLabel = PlantUmlAssert.documentFor(
            pair(UmlAssociation.builder("field", "Target").label("reads").build()));
        String long_ = PlantUmlAssert.documentFor(pair(UmlAssociation.builder("field", "Target")
            .label("consults the price reference").build()));

        assertTrue(nodesep(long_) > nodesep(shortLabel),
            () -> "Short : " + nodesep(shortLabel) + ", long : " + nodesep(long_));
    }

    @Test
    void measuredWidth_ignoresTheEscapedForm() {
        String quotes = PlantUmlAssert.documentFor(
            pair(UmlAssociation.builder("field", "Target").targetRole("\"\"\"\"\"\"\"\"").build()));
        String letters = PlantUmlAssert.documentFor(
            pair(UmlAssociation.builder("field", "Target").targetRole("abcdefgh").build()));

        assertEquals(nodesep(letters), nodesep(quotes),
            "Eight quotes and eight letters take up the same room on the drawing.");
    }

    @Test
    void twoLineLinkName_isMeasuredOnItsLongestLine() {
        String onTwoLines = PlantUmlAssert.documentFor(
            pair(UmlAssociation.builder("field", "Target").label("abcdefgh\nabcdefgh").build()));
        String onALine = PlantUmlAssert.documentFor(
            pair(UmlAssociation.builder("field", "Target").label("abcdefgh").build()));

        assertEquals(nodesep(onALine), nodesep(onTwoLines));
    }

    @Test
    void endpointsAndLinkName_areMeasuredTogether() {
        List<UmlEntity[]> pairs = List.of(
            pair(UmlAssociation.builder("field", "Target").targetRole("abcdefghij").build()),
            pair(UmlAssociation.builder("field", "Target").label("abcdefghij").build()));

        assertEquals(nodesep(PlantUmlAssert.documentFor(pairs.get(0))),
            nodesep(PlantUmlAssert.documentFor(pairs.get(1))),
            "A ten-character role and a ten-character link name reserve as much.");
    }
}
