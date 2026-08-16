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

// What the overall settings write at the head of the generated file: the title, the reading
// reading, the fate of the empty compartments.
//
// The three go to the same place and for the same reason — they aim at no box —,
// hence a single test file. What it must prove of each is twofold: the directive
// expected is written when asked for, and nothing is written when nothing is asked for. A
// a directive set "just in case" is not neutral: it is read in the source, and lies about what
// what the author declared.
class PlantUmlDiagramTest {

    private static final String LINE_HIDING = "hide empty members";
    private static final String LEFT_LINE_RIGHT = "left to right direction";

    // An empty box: the case hideEmptyMembers concerns, that of strategy = NONE.
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

    // --- Le title ---

    @Test
    void declaredTitle_isWrittenAtTheTop() {
        String source = PlantUmlAssert.documentFor(
            settings("Architecture of the Billing module", UMLFlow.TOP_TO_BOTTOM, true),
            emptyBox());

        assertTrue(source.contains("title Architecture of the Billing module"), source);
    }

    @Test
    void withoutTitle_noTitleLineIsWritten() {
        // Not even "title" followed by nothing: the line would take height at the head of the drawing.
        assertFalse(PlantUmlAssert.documentFor(emptyBox()).contains("title"),
            PlantUmlAssert.documentFor(emptyBox()));
    }

    @Test
    void title_isEscaped() {
        // PlantUML reads "title" up to the end of the line: an angle bracket would be taken there for
        // markup, and the user's text must never become any.
        String source = PlantUmlAssert.documentFor(
            settings("General <b>view</b>", UMLFlow.TOP_TO_BOTTOM, true), emptyBox());

        assertFalse(source.contains("<b>"), source);
        assertTrue(source.contains("&#60;b&#62;"), source);
    }

    @Test
    void title_precedesTheBoxes() {
        // The title need not be at the top for PlantUML to understand it; it is there so that the
        // the generated source can be read. The test therefore freezes that order, which no pass added to the
        // the writer must undo.
        String source =
            PlantUmlAssert.documentFor(settings("Billing", UMLFlow.TOP_TO_BOTTOM, true),
                emptyBox());

        assertTrue(source.indexOf("title Billing") < source.indexOf("class \"Client\""),
            source);
    }

    // --- The reading direction ---

    @Test
    void leftToRightDirection_isAskedOfPlantUml() {
        String source = PlantUmlAssert.documentFor(
            settings("", UMLFlow.LEFT_TO_RIGHT, true), emptyBox());

        assertTrue(source.contains(LEFT_LINE_RIGHT), source);
    }

    @Test
    void defaultDirection_writesNoDirective() {
        // TOP_TO_BOTTOM is already what PlantUML does unasked: writing it
        // would suggest a decision where there is none.
        String source = PlantUmlAssert.documentFor(emptyBox());

        assertFalse(source.contains(LEFT_LINE_RIGHT), source);
        assertFalse(source.contains("top to bottom direction"), source);
    }

    // --- The empty compartments ---

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
        // The point of the rework: this setting left the block shared by both appearances, but
        // it did not belong to them for all that. Both styles must follow it
        // identically — it is the rules file that decides, never UmlStyle.
        assertTrue(PlantUmlAssert.documentFor(style, UmlDiagram.defaults(), emptyBox())
            .contains(LINE_HIDING));
        assertFalse(PlantUmlAssert
            .documentFor(style, settings("", UMLFlow.TOP_TO_BOTTOM, false), emptyBox())
            .contains(LINE_HIDING));
    }

    // --- What the settings do not change ---

    @Test
    void theOverallSettings_touchNoBox() {
        // A title and a reading direction say how the drawing presents itself, never what it
        // holds. The generated boxes must therefore be exactly the same.
        UmlEntity[] entities = {emptyBox(), boxPopulated()};

        assertEquals(
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(entities)),
            PlantUmlAssert.namesDeclared(PlantUmlAssert.documentFor(
                settings("One title", UMLFlow.LEFT_TO_RIGHT, false), entities)));
    }

    @Test
    void everyDirective_isAcceptedByPlantUml() {
        // The safety net: is the generated source read without complaint by the engine?
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentFor(
            settings("Architecture « Billing »", UMLFlow.LEFT_TO_RIGHT, false),
            emptyBox(), boxPopulated()));
    }
}
