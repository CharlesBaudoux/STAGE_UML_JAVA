package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// Visual grouping: which boxes enter which rectangle, and what the rectangle
// does not change.
class PlantUmlPackageTest {

    private static UmlEntity box(String name, UmlPackage group) {
        return UmlEntity.builder("com.example." + name, name).group(group).build();
    }

    private static UmlEntity box(String name) {
        return box(name, UmlPackage.none());
    }

    // The lines of the document, stripped of their indentation: the content of a rectangle is
    // written shifted, and that shift must not enter the comparisons.
    private static List<String> lines(String source) {
        return source.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    @Test
    void groupedClass_isFramedByARectangle() {
        String source = PlantUmlAssert.documentFor(
            box("Invoice", new UmlPackage("Module Payment", "")));

        assertTrue(source.contains("package \"Module Payment\" {"), source);
        // The rectangle must close: without the brace, PlantUML would swallow everything that follows.
        assertTrue(lines(source).contains("}"), source);
    }

    @Test
    void colouredGroup_carriesItsColour() {
        String source = PlantUmlAssert.documentFor(
            box("Invoice", new UmlPackage("Module Payment", "#EEEEEE")));

        assertTrue(source.contains("package \"Module Payment\" #EEEEEE {"), source);
    }

    @Test
    void classWithoutGroup_isFramedByNothing() {
        assertFalse(PlantUmlAssert.documentFor(box("Invoice")).contains("package "),
            "A class without @UMLPackage is drawn at the top level.");
    }

    @Test
    void twoClassesOfTheSameGroup_shareASingleRectangle() {
        UmlPackage group = new UmlPackage("Billing", "");
        String source = PlantUmlAssert.documentFor(
            box("Invoice", group), box("Line", group));

        // A single opening for both: it is the name that makes the identity of the group.
        assertEquals(1, source.lines().filter(l -> l.startsWith("package ")).count(), source);
        assertTrue(source.indexOf("Invoice") > source.indexOf("package "), source);
    }

    @Test
    void twoDistinctGroups_giveTwoRectangles() {
        String source = PlantUmlAssert.documentFor(
            box("Invoice", new UmlPackage("Billing", "")),
            box("Account", new UmlPackage("Security", "")));

        assertEquals(2, source.lines().filter(l -> l.startsWith("package ")).count(), source);
    }

    @Test
    void groupedAndFreeClasses_coexist() {
        String source = PlantUmlAssert.documentFor(
            box("Free"), box("Filed", new UmlPackage("Billing", "")));

        // The boxes without a group come first: they have no rectangle to open, and the
        // the source reads better that way.
        assertTrue(source.indexOf("\"Free\"") < source.indexOf("package "), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void contradictoryColours_theFirstOneWins() {
        // A rectangle has only one colour. The first one met is kept, and the difference is
        // reported to standard error — which this test does not measure, but which explains
        // why the second vanishes with nothing failing.
        String source = PlantUmlAssert.documentFor(
            box("Invoice", new UmlPackage("Billing", "#EEEEEE")),
            box("Line", new UmlPackage("Billing", "#123456")));

        assertTrue(source.contains("#EEEEEE"), source);
        assertFalse(source.contains("#123456"), source);
    }

    @Test
    void colourDeclaredAfterAColourlessGroup_isKept() {
        // Nothing stands against it: no colour had been chosen yet, and losing it would be
        // gratuit.
        String source = PlantUmlAssert.documentFor(
            box("Invoice", new UmlPackage("Billing", "")),
            box("Line", new UmlPackage("Billing", "#123456")));

        assertTrue(source.contains("package \"Billing\" #123456 {"), source);
    }

    // What the library wrote to standard error during the rendering.
    //
    // The stream is replaced for the duration of the call, then restored: a test leaving behind it
    // a diverted System.err would skew every later one, hence the try/finally.
    private static String errorDuringRendering(UmlEntity... entities) {
        java.io.PrintStream original = System.err;
        java.io.ByteArrayOutputStream capture = new java.io.ByteArrayOutputStream();
        try {
            System.setErr(new java.io.PrintStream(capture, true,
                java.nio.charset.StandardCharsets.UTF_8));
            PlantUmlAssert.documentFor(entities);
        } finally {
            System.setErr(original);
        }
        return capture.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void colourAddedToAColourlessGroup_reportsNothing() {
        // A warning announcing the loss of a colour actually drawn would be worse
        // than no warning at all: it teaches one to stop reading them.
        assertTrue(errorDuringRendering(
            box("Invoice", new UmlPackage("Billing", "")),
            box("Line", new UmlPackage("Billing", "#123456"))).isEmpty(),
            "No colour is lost: there is nothing to report.");
    }

    @Test
    void colourReallyLost_isReported() {
        String errors = errorDuringRendering(
            box("Invoice", new UmlPackage("Billing", "#EEEEEE")),
            box("Line", new UmlPackage("Billing", "#123456")));

        assertTrue(errors.contains("#123456"), errors);
        assertTrue(errors.contains("com.example.Line"), errors);
    }

    @Test
    void colourlessClassInAColouredGroup_reportsNothing() {
        assertTrue(errorDuringRendering(
            box("Invoice", new UmlPackage("Billing", "#EEEEEE")),
            box("Line", new UmlPackage("Billing", ""))).isEmpty(),
            "A class asking for no colour loses none.");
    }

    @Test
    void unknownColour_isRefused() {
        // The same requirement as for a box: the colour vocabulary is that of the engine
        // of rendering, and it is closed.
        assertThrows(IllegalArgumentException.class, () -> PlantUmlAssert.documentFor(
            box("Invoice", new UmlPackage("Billing", "NotAColor"))));
    }

    @Test
    void groupName_isEscaped() {
        String source = PlantUmlAssert.documentFor(
            box("Invoice", new UmlPackage("View <b>wide</b>", "")));

        assertFalse(source.contains("<b>"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void aLinkCrossesTheRectangles() {
        // An association between two groups stays an ordinary stroke: relations are
        // written outside the rectangles, after every box.
        UmlEntity source = UmlEntity.builder("com.example.Invoice", "Invoice")
            .group(new UmlPackage("Billing", ""))
            .associations(List.of(
                UmlAssociation.builder("account", "com.example.Account").build()))
            .build();
        UmlEntity target = box("Account", new UmlPackage("Security", ""));

        String document = PlantUmlAssert.documentFor(source, target);

        assertEquals(1, PlantUmlAssert.linkLines(document).size(), document);
        PlantUmlAssert.assertCompile(document);
    }

    @Test
    void theGroup_changesNoBox() {
        // A rectangle files the boxes; it does not change them.
        assertEquals(
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(box("Invoice"))),
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(
                box("Invoice", new UmlPackage("Billing", "#EEEEEE")))));
    }
}
