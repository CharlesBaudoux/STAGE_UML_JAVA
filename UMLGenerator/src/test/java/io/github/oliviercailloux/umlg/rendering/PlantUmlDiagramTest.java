package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PlantUmlDiagramTest {

    private static final String LINE_HIDING = "hide empty members";
    private static final String LEFT_LINE_RIGHT = "left to right direction";

    private static UmlEntity emptyBox() {
        return UmlEntity.builder("com.example.Client", "Client").build();
    }

    private static UmlEntity boxPopulated() {
        return UmlEntity.builder("com.example.Account", "Account")
            .fields(List.of(new UmlField("balance", "double", "-", false))).build();
    }

    private static UmlDiagram settings(String title, UMLFlow meaning, boolean hide) {
        return new UmlDiagram(title, meaning, hide);
    }

    @Test
    void declaredTitle_isWrittenAtTheTop() {
        String source = PlantUmlAssert.documentFor(
            settings("Architecture of the Billing module", UMLFlow.TOP_TO_BOTTOM, true),
            emptyBox());

        assertTrue(source.contains("title Architecture of the Billing module"), source);
    }

    @Test
    void withoutTitle_noTitleLineIsWritten() {
        assertFalse(PlantUmlAssert.documentFor(emptyBox()).contains("title"),
            PlantUmlAssert.documentFor(emptyBox()));
    }

    @Test
    void title_isEscaped() {
        String source = PlantUmlAssert.documentFor(
            settings("General <b>view</b>", UMLFlow.TOP_TO_BOTTOM, true), emptyBox());

        assertFalse(source.contains("<b>"), source);
        assertTrue(source.contains("&#60;b&#62;"), source);
    }

    @Test
    void title_precedesTheBoxes() {
        String source =
            PlantUmlAssert.documentFor(settings("Billing", UMLFlow.TOP_TO_BOTTOM, true),
                emptyBox());

        assertTrue(source.indexOf("title Billing") < source.indexOf("class \"Client\""),
            source);
    }

    @Test
    void leftToRightDirection_isAskedOfPlantUml() {
        String source = PlantUmlAssert.documentFor(
            settings("", UMLFlow.LEFT_TO_RIGHT, true), emptyBox());

        assertTrue(source.contains(LEFT_LINE_RIGHT), source);
    }

    @Test
    void defaultDirection_writesNoDirective() {
        String source = PlantUmlAssert.documentFor(emptyBox());

        assertFalse(source.contains(LEFT_LINE_RIGHT), source);
        assertFalse(source.contains("top to bottom direction"), source);
    }

    @Test
    void byDefault_theEmptyCompartmentsAreHidden() {
        assertTrue(PlantUmlAssert.documentFor(emptyBox()).contains(LINE_HIDING));
    }

    @Test
    void refusedHiding_theDirectiveVanishes() {
        String source = PlantUmlAssert.documentFor(
            settings("", UMLFlow.TOP_TO_BOTTOM, false), emptyBox());

        assertFalse(source.contains(LINE_HIDING), source);
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void theHiding_doesNotDependOnTheAppearance(UmlStyle style) {
        assertTrue(PlantUmlAssert.documentFor(style, UmlDiagram.defaults(), emptyBox())
            .contains(LINE_HIDING));
        assertFalse(PlantUmlAssert
            .documentFor(style, settings("", UMLFlow.TOP_TO_BOTTOM, false), emptyBox())
            .contains(LINE_HIDING));
    }

    @Test
    void theOverallSettings_touchNoBox() {
        UmlEntity[] entities = {emptyBox(), boxPopulated()};

        assertEquals(
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(entities)),
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(
                settings("One title", UMLFlow.LEFT_TO_RIGHT, false), entities)));
    }

    @Test
    void everyDirective_isAcceptedByPlantUml() {
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentFor(
            settings("Architecture « Billing »", UMLFlow.LEFT_TO_RIGHT, false),
            emptyBox(), boxPopulated()));
    }
}
