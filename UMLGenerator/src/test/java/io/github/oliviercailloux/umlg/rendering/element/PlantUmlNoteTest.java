package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlNote;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlantUmlNoteTest {

    private static UmlEntity box(List<UmlNote> notes) {
        return UmlEntity.builder("com.example.Client", "Client")
            .fields(List.of(new UmlField("balance", "double", "-", false)))
            .notes(notes).build();
    }

    private static String source(UmlNote... notes) {
        return PlantUmlAssert.documentFor(box(List.of(notes)));
    }

    private static List<String> noteLines(String source) {
        return source.lines().filter(l -> l.startsWith("note ")).toList();
    }

    private static List<String> noteBloc(String source) {
        return noteBloc(source, 3);
    }

    private static List<String> noteBloc(String source, int height) {
        List<String> lines = source.lines().toList();
        int start = lines.indexOf(lines.stream().filter(l -> l.startsWith("note "))
            .findFirst().orElseThrow());
        return lines.subList(start, start + height);
    }

    @Test
    void classNote_hangsOnTheBox() {
        String source = source(UmlNote.onClass("Dette technical assumee", UMLDirection.RIGHT));

        assertEquals(List.of("note right of Client : Dette technical assumee"),
            noteLines(source));
        PlantUmlAssert.assertCompile(source);
    }

    @ParameterizedTest
    @CsvSource({"NONE,right", "RIGHT,right", "LEFT,left", "UP,top", "DOWN,bottom"})
    void direction_isTranslatedIntoPlantUmlVocabulary(UMLDirection direction, String expected) {
        String source = source(UmlNote.onClass("Text", direction));

        assertEquals(List.of("note " + expected + " of Client : Text"), noteLines(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void memberNote_hangsOnTheMember() {
        String source = source(new UmlNote("Never negative", UMLDirection.RIGHT, "balance"));

        assertEquals(List.of("note right of Client::balance", "&#78;ever negative", "end note"),
            noteBloc(source));
        PlantUmlAssert.assertCompile(source);
    }

    @ParameterizedTest
    @CsvSource({"NONE,right", "RIGHT,right", "LEFT,left", "UP,right", "DOWN,right"})
    void directionOnAMember_fallsBackOnASidePlantUmlKnows(UMLDirection direction,
        String expected) {
        String source = source(new UmlNote("Text", direction, "balance"));

        assertEquals("note " + expected + " of Client::balance", noteBloc(source).get(0), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void memberNote_multilineText_usesRealBreaks() {
        String source = source(new UmlNote("Premiere\nSeconde", UMLDirection.RIGHT, "balance"));

        assertEquals(
            List.of("note right of Client::balance", "&#80;remiere", "&#83;econde", "end note"),
            noteBloc(source, 4), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void memberNote_multilineText_isReadOverSeveralLines() throws Exception {
        String text = PlantUmlAssert.textRendered(
            box(List.of(new UmlNote("Premiere\nSeconde", UMLDirection.RIGHT, "balance"))));

        assertFalse(text.contains("\\n"), "The line break is displayed in full:\n"
            + text);
        assertTrue(text.contains("Premiere"), text);
        assertTrue(text.contains("Second"), text);
    }

    @Test
    void memberNote_innerLineImitatingTheTerminator_closesNothing() {
        String source =
            source(new UmlNote("Anodin\nend note\nclass Pirate", UMLDirection.RIGHT, "balance"));

        assertEquals(1, PlantUmlAssert.diagramsCount(source), source);
        assertFalse(PlantUmlAssert.aliasDeclared(source).contains("Pirate"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void memberNote_textImitatingTheTerminator_closesNothing() {
        String source = source(new UmlNote("end note", UMLDirection.RIGHT, "balance"));

        assertEquals(List.of("note right of Client::balance", "&#101;nd note", "end note"),
            noteBloc(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void memberNote_readerSeesTheImitatingText() throws Exception {
        String text = PlantUmlAssert.textRendered(
            box(List.of(new UmlNote("end note", UMLDirection.RIGHT, "balance"))));

        assertTrue(text.contains("end note"), text);
    }

    @Test
    void severalNotes_allComeOut() {
        String source = source(UmlNote.onClass("Premiere", UMLDirection.UP),
            UmlNote.onClass("Second", UMLDirection.DOWN));

        assertEquals(2, noteLines(source).size(), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void multilineText_staysOnASingleLine() {
        String source = source(UmlNote.onClass("Premiere\nSeconde", UMLDirection.RIGHT));

        assertEquals(1, noteLines(source).size(), source);
        assertTrue(noteLines(source).get(0).contains("\\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void textContainingEndNote_closesNothing() {
        String source =
            source(UmlNote.onClass("end note\n@enduml\nclass Pirate", UMLDirection.RIGHT));

        assertEquals(1, PlantUmlAssert.diagramsCount(source),
            "The text cut the document:\n" + source);
        assertFalse(PlantUmlAssert.aliasDeclared(source).contains("Pirate"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void readerSeesText() throws Exception {
        String text = PlantUmlAssert.textRendered(
            box(List.of(UmlNote.onClass("Algorithm critical", UMLDirection.RIGHT))));

        assertTrue(text.contains("Algorithm critical"), text);
    }
}
