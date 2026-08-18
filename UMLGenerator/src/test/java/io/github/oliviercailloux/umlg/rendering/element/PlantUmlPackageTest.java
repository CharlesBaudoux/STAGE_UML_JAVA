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

class PlantUmlPackageTest {

    private static UmlEntity box(String name, UmlPackage group) {
        return UmlEntity.builder("com.example." + name, name).group(group).build();
    }

    private static UmlEntity box(String name) {
        return box(name, UmlPackage.none());
    }

    private static List<String> lines(String source) {
        return source.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    @Test
    void groupedClass_isFramedByARectangle() {
        String source = PlantUmlAssert.documentFor(
            box("Invoice", new UmlPackage("Module Payment", "")));

        assertTrue(source.contains("package \"Module Payment\" {"), source);
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

        assertTrue(source.indexOf("\"Free\"") < source.indexOf("package "), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void contradictoryColours_theFirstOneWins() {
        String source = PlantUmlAssert.documentFor(
            box("Invoice", new UmlPackage("Billing", "#EEEEEE")),
            box("Line", new UmlPackage("Billing", "#123456")));

        assertTrue(source.contains("#EEEEEE"), source);
        assertFalse(source.contains("#123456"), source);
    }

    @Test
    void colourDeclaredAfterAColourlessGroup_isKept() {
        String source = PlantUmlAssert.documentFor(
            box("Invoice", new UmlPackage("Billing", "")),
            box("Line", new UmlPackage("Billing", "#123456")));

        assertTrue(source.contains("package \"Billing\" #123456 {"), source);
    }

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
        assertEquals(
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(box("Invoice"))),
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(
                box("Invoice", new UmlPackage("Billing", "#EEEEEE")))));
    }
}
