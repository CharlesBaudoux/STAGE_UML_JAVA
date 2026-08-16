package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import org.junit.jupiter.api.Test;

// What extraction draws from @UMLPackage.
//
// Note: the faulty classes of the refusal tests carry no @UMLClass, and the tests
// call PackageExtractor directly. That is intended — otherwise the scan of target/test-classes,
// in the integration tests, would meet them and fail.
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

    // An impossible hexadecimal: what the form check refuses, the only one applying here.
    // A colour name unknown to the engine — "NotAColor" — has the form of a colour and does
    // is refused only at rendering time, by ColorCodec.
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
        // The same grammar as for a box, and the message names the annotation actually
        // written — @UMLPackage, and not @UMLClass.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PackageExtractor.extract(ColorBadlyFormed.class));

        assertTrue(e.getMessage().contains("@UMLPackage"), e.getMessage());
        // The grammar is that of the PlantUML catalogue, the same as on the drawing and at
        // compilation: a single question put to a single judge.
        assertTrue(e.getMessage().contains("Color unknown to PlantUML"), e.getMessage());
    }

    @Test
    void groupWithoutNameButWithColour_isRefusedByTheModel() {
        // The case cannot come from the annotation — name() is mandatory there — but the model
        // checks itself: a colour without a group would be set on nothing.
        assertThrows(IllegalArgumentException.class, () -> new UmlPackage("", "#EEEEEE"));
    }
}
