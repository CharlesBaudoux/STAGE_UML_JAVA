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

// The rendering of comment bubbles.
//
// Two concerns: the right placement (PlantUML accepts no note without a side, and its
// the vocabulary is not that of the enumerated one), and the safety of the text — it is the only place of the
// a diagram where the user writes a whole sentence.
class PlantUmlNoteTest {

    private static UmlEntity box(List<UmlNote> notes) {
        return UmlEntity.builder("com.example.Client", "Client")
            .fields(List.of(new UmlField("balance", "double", "-", false)))
            .notes(notes).build();
    }

    private static String source(UmlNote... notes) {
        return PlantUmlAssert.documentFor(box(List.of(notes)));
    }

    // The lines that open a note.
    private static List<String> noteLines(String source) {
        return source.lines().filter(l -> l.startsWith("note ")).toList();
    }

    // The whole block of a member note: the opening, the text, the terminator.
    private static List<String> noteBloc(String source) {
        return noteBloc(source, 3);
    }

    // The same thing, when the text takes more than one line: the block then counts as many
    // plus.
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
        // UP and DOWN are not said "up" and "down": a mere lowercasing
        // would produce a syntax PlantUML refuses. NONE must choose a side, otherwise
        // the note would have nowhere to sit.
        String source = source(UmlNote.onClass("Text", direction));

        assertEquals(List.of("note " + expected + " of Client : Text"), noteLines(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void memberNote_hangsOnTheMember() {
        // PlantUML refuses the single-line form for a member: only the block works.
        // The text is written there with its first character encoded, which makes it unable
        // of imitating the terminator.
        String source = source(new UmlNote("Never negative", UMLDirection.RIGHT, "balance"));

        assertEquals(List.of("note right of Client::balance", "&#78;ever negative", "end note"),
            noteBloc(source));
        PlantUmlAssert.assertCompile(source);
    }

    @ParameterizedTest
    @CsvSource({"NONE,right", "RIGHT,right", "LEFT,left", "UP,right", "DOWN,right"})
    void directionOnAMember_fallsBackOnASidePlantUmlKnows(UMLDirection direction,
        String expected) {
        // PlantUML places a member note on the left or on the right only: "note top of A::m"
        // is not degraded, it is refused, and its refusal carries away the whole document. UP and DOWN
        // must therefore fall back, otherwise a single misdirected annotation would replace the
        // the diagram by an error image.
        String source = source(new UmlNote("Text", direction, "balance"));

        assertEquals("note " + expected + " of Client::balance", noteBloc(source).get(0), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void memberNote_multilineText_usesRealBreaks() {
        // A block does not interpret the "\n" sequence: it would be displayed there in full.
        // So it must write real line breaks — the exact opposite of the
        // form on one only line.
        String source = source(new UmlNote("Premiere\nSeconde", UMLDirection.RIGHT, "balance"));

        assertEquals(
            List.of("note right of Client::balance", "&#80;remiere", "&#83;econde", "end note"),
            noteBloc(source, 4), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void memberNote_multilineText_isReadOverSeveralLines() throws Exception {
        // The only level proving the fix: what the reader sees. A literal "\n"
        // would appear here in the middle of the sentence.
        String text = PlantUmlAssert.textRendered(
            box(List.of(new UmlNote("Premiere\nSeconde", UMLDirection.RIGHT, "balance"))));

        assertFalse(text.contains("\\n"), "The line break is displayed in full:\n"
            + text);
        assertTrue(text.contains("Premiere"), text);
        assertTrue(text.contains("Second"), text);
    }

    @Test
    void memberNote_innerLineImitatingTheTerminator_closesNothing() {
        // Splitting into lines opens a breach the single-piece form did not have:
        // any line of the text becomes a line of the block. The protection therefore bears
        // on everything, and no longer on the first one alone.
        String source =
            source(new UmlNote("Anodin\nend note\nclass Pirate", UMLDirection.RIGHT, "balance"));

        assertEquals(1, PlantUmlAssert.diagramsCount(source), source);
        assertFalse(PlantUmlAssert.aliasDeclared(source).contains("Pirate"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void memberNote_textImitatingTheTerminator_closesNothing() {
        // The case warranting the encoding: without it, this note would close on itself and
        // PlantUML would reject the whole document.
        String source = source(new UmlNote("end note", UMLDirection.RIGHT, "balance"));

        assertEquals(List.of("note right of Client::balance", "&#101;nd note", "end note"),
            noteBloc(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void memberNote_readerSeesTheImitatingText() throws Exception {
        // The encoding must not show: the reader indeed reads "end note".
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

    // --- The safety of the text ---

    @Test
    void multilineText_staysOnASingleLine() {
        // The line break becomes the "\n" sequence, which PlantUML displays as a break of
        // line. What counts here is that no new line appears in the source: the
        // the rest of the text would then be read as syntax.
        String source = source(UmlNote.onClass("Premiere\nSeconde", UMLDirection.RIGHT));

        assertEquals(1, noteLines(source).size(), source);
        assertTrue(noteLines(source).get(0).contains("\\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void textContainingEndNote_closesNothing() {
        // The reason for the single-line form: in the block form, this sentence
        // would close the bubble and what follows would be written as PlantUML syntax.
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
