package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import org.junit.jupiter.api.Test;

class PackageExtractorTest {

    @UMLClass
    @UMLPackage(name = "Module Payment", color = "#EEEEEE")
    static class Invoice {}

    @UMLClass
    @UMLPackage(name = "Module Payment")
    static class Line {}

    @UMLClass
    static class OutsideGroup {}

    @UMLPackage(name = "   ")
    static class BlankName {}

    @UMLPackage(name = "Group", color = "#GGGGGG")
    static class ColorBadlyFormed {}

    @Test
    void declaredGroup_isRead() {
        assertEquals(new UmlPackage("Module Payment", "#EEEEEE"),
            EntityExtractor.extract(Invoice.class).group());
    }

    @Test
    void colourlessGroup_hasAnEmptyColour() {
        UmlPackage group = EntityExtractor.extract(Line.class).group();

        assertEquals("Module Payment", group.name());
        assertEquals("", group.color());
        assertTrue(group.isGrouped());
    }

    @Test
    void classWithoutAnnotation_isInNoGroup() {
        UmlPackage group = EntityExtractor.extract(OutsideGroup.class).group();

        assertEquals(UmlPackage.none(), group);
        assertFalse(group.isGrouped());
    }

    @Test
    void blankName_isRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PackageExtractor.extract(BlankName.class));

        assertTrue(e.getMessage().contains("name()"), e.getMessage());
    }

    @Test
    void malformedColour_isRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PackageExtractor.extract(ColorBadlyFormed.class));

        assertTrue(e.getMessage().contains("@UMLPackage"), e.getMessage());
        assertTrue(e.getMessage().contains("Color unknown to PlantUML"), e.getMessage());
    }

    @Test
    void groupWithoutNameButWithColour_isRefusedByTheModel() {
        assertThrows(IllegalArgumentException.class, () -> new UmlPackage("", "#EEEEEE"));
    }
}
