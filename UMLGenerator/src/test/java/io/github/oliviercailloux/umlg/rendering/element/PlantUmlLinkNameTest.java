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

// The text set on a stroke: its styling, and the room reserved for it.
//
// Two subjects that are but one, because they bear on the same text. The styling must
// survive a line break — PlantUML cuts the label without carrying the tags over
// open —, and the spacing of the diagram must be computed on what the reader sees, not on
// the escaped form that is written in the file.
class PlantUmlLinkNameTest {

    private static final Pattern NODESEP = Pattern.compile("skinparam nodesep (\\d+)");

    // The minimal pair: a relation needs both its endpoints declared.
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

    // The stroke line, as it is written in the source — styling included, which
    // PlantUmlAssert.linkLines retire justement.
    private static String strokeLine(String source) {
        return source.lines()
            .filter(l -> l.startsWith("Source "))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No stroke:\n" + source));
    }

    // --- A link name over two lines ---

    @Test
    void twoLineLinkName_stylesEachLineSeparately() {
        // The defect: "<i>line1\nline2</i>". PlantUML cuts at the "\n" without carrying the tags over,
        // and the second line lost its italics while displaying the closing tags.
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
        // The level that counts: what the reader sees. "line2</i></color>" was displayed in
        // in full on the stroke, and nothing caught it — the source is valid, hence the
        // the writer's check saw no error.
        String svg = PlantUmlAssert.svg(UmlStyle.UMLG,
            pair(UmlAssociation.builder("field", "Target").label("lig1\nlig2").build()));

        assertFalse(svg.contains("&lt;/i&gt;"), "A closing tag is drawn:\n" + svg);
        assertFalse(svg.contains("&lt;/color&gt;"), "A closing tag is drawn:\n" + svg);
    }

    @Test
    void singleLineLinkName_isStyledAsBefore() {
        // The nominal case must gain nothing: a single styling, and no "\n".
        String stroke = strokeLine(PlantUmlAssert.documentFor(UmlStyle.UMLG,
            pair(UmlAssociation.builder("field", "Target").label("holds").build())));

        assertTrue(stroke.endsWith(" : <color:#3C5F82><i>holds</i></color>"), stroke);
    }

    // --- A name escaping empties ---

    @Test
    void linkNameMadeOfInvisibleCharacters_writesNoStyling() {
        // Escaping removes control characters and can therefore empty the string. The
        // the upstream check tested the original text, which was not empty: "<i></i>" was written, and
        // PlantUML draws it as "</i>" on the stroke.
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

    // --- Spacing, measured on what is drawn ---

    @Test
    void longLinkName_pushesTheBoxesApart() {
        // UmlStyle announces "the spacing, computed on the longest label of the diagram".
        // Only the endpoints were measured; the link name — the longest text of a
        // stroke in practice — is nevertheless drawn in the same place, between two neighbouring boxes.
        String shortLabel = PlantUmlAssert.documentFor(
            pair(UmlAssociation.builder("field", "Target").label("reads").build()));
        String long_ = PlantUmlAssert.documentFor(pair(UmlAssociation.builder("field", "Target")
            .label("consults the price reference").build()));

        assertTrue(nodesep(long_) > nodesep(shortLabel),
            () -> "Short : " + nodesep(shortLabel) + ", long : " + nodesep(long_));
    }

    @Test
    void measuredWidth_ignoresTheEscapedForm() {
        // A quote takes one character on screen and five in the source ("&#34;"). Measured
        // on the source, a label of quotes passed for five times wider than it
        // is, and the boxes moved apart for room nothing occupies.
        String quotes = PlantUmlAssert.documentFor(
            pair(UmlAssociation.builder("field", "Target").targetRole("\"\"\"\"\"\"\"\"").build()));
        String letters = PlantUmlAssert.documentFor(
            pair(UmlAssociation.builder("field", "Target").targetRole("abcdefgh").build()));

        assertEquals(nodesep(letters), nodesep(quotes),
            "Eight quotes and eight letters take up the same room on the drawing.");
    }

    @Test
    void twoLineLinkName_isMeasuredOnItsLongestLine() {
        // Two lines are drawn one under the other: the width is that of the longest,
        // and not their sum.
        String onTwoLines = PlantUmlAssert.documentFor(
            pair(UmlAssociation.builder("field", "Target").label("abcdefgh\nabcdefgh").build()));
        String onALine = PlantUmlAssert.documentFor(
            pair(UmlAssociation.builder("field", "Target").label("abcdefgh").build()));

        assertEquals(nodesep(onALine), nodesep(onTwoLines));
    }

    @Test
    void endpointsAndLinkName_areMeasuredTogether() {
        // The two texts fight over the same room; the widest one decides.
        List<UmlEntity[]> pairs = List.of(
            pair(UmlAssociation.builder("field", "Target").targetRole("abcdefghij").build()),
            pair(UmlAssociation.builder("field", "Target").label("abcdefghij").build()));

        assertEquals(nodesep(PlantUmlAssert.documentFor(pairs.get(0))),
            nodesep(PlantUmlAssert.documentFor(pairs.get(1))),
            "A ten-character role and a ten-character link name reserve as much.");
    }
}
