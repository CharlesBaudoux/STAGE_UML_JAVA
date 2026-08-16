package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlLayout;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// The invisible link a view sets between two blocks: present in the source, absent from the drawing.
class PlantUmlLayoutTest {

    private static UmlEntity target() {
        return UmlEntity.builder("com.example.Engine", "Engine").build();
    }

    private static UmlEntity source(UMLDirection direction) {
        return UmlEntity.builder("com.example.Car", "Car")
            .relations(List.of(new UmlLayout("com.example.Engine", direction)))
            .build();
    }

    private static String lineUnique(UMLDirection direction) {
        return PlantUmlAssert.linkLines(
            PlantUmlAssert.documentFor(source(direction), target())).get(0);
    }

    @ParameterizedTest
    @CsvSource({"UP,up", "DOWN,down", "LEFT,left", "RIGHT,right"})
    void requestedDirection_entersTheConnector(UMLDirection direction, String expected) {
        assertTrue(lineUnique(direction).contains("-[hidden]" + expected + "->"),
            lineUnique(direction));
    }

    @Test
    void withoutDirection_theLinkStaysBare() {
        // The link exists all the same: it brings the two boxes together without saying on which side.
        assertTrue(lineUnique(UMLDirection.NONE).contains("-[hidden]->"),
            lineUnique(UMLDirection.NONE));
    }

    @Test
    void theLink_carriesNoLabel() {
        // Nothing could read it: the line is not drawn.
        assertEquals(-1, lineUnique(UMLDirection.DOWN).indexOf(" : "),
            lineUnique(UMLDirection.DOWN));
    }

    @Test
    void theTarget_mustHaveBeenDeclared() {
        // An invisible link stays a link: its endpoint is a box of the diagram, and its
        // absence is reported as for any other relation.
        assertThrows(IllegalArgumentException.class,
            () -> PlantUmlAssert.documentFor(source(UMLDirection.DOWN)));
    }

    @Test
    void thePlacement_changesNoBox() {
        assertEquals(
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(
                UmlEntity.builder("com.example.Car", "Car").build(), target())),
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(
                source(UMLDirection.DOWN), target())));
    }

    @Test
    void theEngineAcceptsTheSyntax() {
        PlantUmlAssert.assertCompile(
            PlantUmlAssert.documentFor(source(UMLDirection.DOWN), target()));
    }
}
