package io.github.oliviercailloux.umlg.rendering.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PlantUmlReadabilityTest {

    private static final Pattern TEXT_SVG =
        Pattern.compile("<text[^>]*fill=\"(#[0-9A-Fa-f]{6})\"([^>]*)>([^<]*)</text>");

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

    private static String decode(String fragment) {
        Matcher m = Pattern.compile("&#(\\d+);").matcher(fragment);
        StringBuilder output = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(output,
                Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
        }
        return m.appendTail(output).toString();
    }

    private static String appearance(String svg, String content) {
        Matcher m = TEXT_SVG.matcher(svg);
        while (m.find()) {
            if (decode(m.group(3)).strip().equals(content)) {
                return m.group(1) + (m.group(2).contains("italic") ? " italic" : " droit");
            }
        }
        throw new AssertionError("Text \"" + content + "\" missing from the rendering:\n" + svg);
    }

    @Test
    void linkName_andMultiplicity_doNotLookAlike() throws Exception {
        String svg = PlantUmlAssert.svg(pair(UmlAssociation.builder("field", "Target")
            .targetMultiplicity("0..*").label("holds").build()));

        assertNotEquals(appearance(svg, "0..*"), appearance(svg, "holds"),
            "The link name and the multiplicity must be told apart at a glance.");
    }

    @Test
    void dependencyName_isStyledLikeAnAssociationName() throws Exception {
        UmlEntity source = UmlEntity.builder("Source", "Source")
            .dependencies(ImmutableList.of(new UmlDependency("Target", "audite"))).build();
        String svg = PlantUmlAssert.svg(source, UmlEntity.builder("Target", "Target").build());

        String assoc = PlantUmlAssert.svg(pair(UmlAssociation.builder("field", "Target")
            .label("audite").build()));

        assertEquals(appearance(assoc, "audite"), appearance(svg, "audite"),
            "Association and dependency both name a stroke: same treatment.");
    }

    @Test
    void linkName_survivesADoubleSlashInTheText() throws Exception {
        String svg = PlantUmlAssert.svg(pair(UmlAssociation.builder("field", "Target")
            .label("reads//writes").build()));

        assertTrue(decode(svg).contains("reads//writes"),
            () -> "Le text must s'display tel which, « // » compris :\n" + svg);
    }

    @Test
    void longLabel_pushesTheBoxesFurtherApart() {
        String shortLabel = PlantUmlAssert.documentFor(pair(
            UmlAssociation.builder("field", "Target").targetMultiplicity("1").build()));
        String long_ = PlantUmlAssert.documentFor(pair(
            UmlAssociation.builder("field", "Target")
                .targetRole("holderAccounts").targetMultiplicity("1..*")
                .constraints(ImmutableList.of("ordered")).build()));

        assertTrue(nodesep(long_) > nodesep(shortLabel),
            () -> "An endpoint label is drawn between two boxes: the longer it is, "
                + "long, the more room it needs, otherwise it spills over the neighbouring one. "
                + "Short : " + nodesep(shortLabel) + ", long : " + nodesep(long_));
    }

    @Test
    void diagramWithoutLabel_staysCompact() {
        String without = PlantUmlAssert.documentFor(
            pair(UmlAssociation.builder("field", "Target").build()));
        String with = PlantUmlAssert.documentFor(pair(
            UmlAssociation.builder("field", "Target").targetRole("aFairlyLongRole").build()));

        assertTrue(nodesep(without) < nodesep(with),
            "With not the slightest label, nothing warrants spreading the diagram out.");
    }
}
