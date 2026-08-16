package io.github.oliviercailloux.umlg.rendering.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlNote;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Creole markup that opens only at the head of a drawn line.
//
// PlantUML reads two markup grammars. The first opens anywhere in a line, with
// doubled markers — "**bold**", "[[link]]" —, and PlantUmlEscapeTest covers it. The
// the second applies only to the first character of a rendered line: "#" and "*" a bulleted
// bullets, "=" a title, "|" a table row. It was not covered, and it got through:
// a note reading "# Warning" was drawn as "1. Warning", a box name reading
// "= title" lost its equal sign.
//
// These tests go all the way to the SVG: the only place where one sees what the reader sees. A
// a numeric entity present in the source is exactly what is expected — it is on screen
// that it becomes the written character again.
class PlantUmlLineStartTest {

    // The four characters concerned, each at the head of a plausible text.
    private static final List<String> TEXTS_A_RISK = List.of(
        "# Warning", "* see the contract", "= title", "|a|b|");

    private static UmlEntity named(String name) {
        return new UmlEntity(name, "", "", UMLVisibility.PUBLIC, List.of(), List.of());
    }

    // --- Position 1: the name of a box ---

    @ParameterizedTest
    @ValueSource(strings = {"# Warning", "* see the contract", "= title", "|a|b|"})
    void boxName_keepsItsFirstCharacter(String name) throws Exception {
        String rendered = PlantUmlAssert.textRendered(named(name));

        assertTrue(rendered.contains(name),
            "The name \"" + name + "\" was read as markup. Drawn text:\n" + rendered);
    }

    // --- Position 2: the name of a group rectangle ---

    @ParameterizedTest
    @ValueSource(strings = {"# Warning", "* see the contract", "= title", "|a|b|"})
    void groupName_keepsItsFirstCharacter(String name) throws Exception {
        UmlEntity filed = UmlEntity.builder("com.example.Invoice", "Invoice")
            .group(new UmlPackage(name, "")).build();

        String rendered = PlantUmlAssert.textRendered(filed);

        assertTrue(rendered.contains(name),
            "The group name \"" + name + "\" was read as markup:\n" + rendered);
    }

    // --- Position 3: the title of the diagram ---

    @Test
    void diagramTitle_keepsItsEqualSigns() throws Exception {
        // The case quoted by the audit: "=== Billing ===" was drawn as "Billing ===",
        // the first three equal signs consumed as a Creole title.
        UmlDiagram settings = new UmlDiagram("=== Billing ===", UMLFlow.TOP_TO_BOTTOM, true);
        String source =
            PlantUmlAssert.documentFor(settings, named("Invoice"));

        PlantUmlAssert.assertCompile(source);
        assertTrue(source.contains("title &#61;== Billing ==="),
            "Only the first character is encoded, and that is enough:\n" + source);
    }

    // --- Position 4: a single-line note ---

    @ParameterizedTest
    @ValueSource(strings = {"# Warning", "* see the contract", "= title", "|a|b|"})
    void classNote_keepsItsFirstCharacter(String text) throws Exception {
        UmlEntity withNote = UmlEntity.builder("com.example.Invoice", "Invoice")
            .notes(ImmutableList.of(UmlNote.onClass(text, UMLDirection.RIGHT))).build();

        String rendered = PlantUmlAssert.textRendered(withNote);

        assertTrue(rendered.contains(text),
            "The note \"" + text + "\" was read as markup:\n" + rendered);
    }

    // --- Position 5: a member whose visibility is silenced ---

    @ParameterizedTest
    @ValueSource(strings = {"#total", "*total", "=total", "|total"})
    void memberWithoutVisibility_keepsItsFirstCharacter(String name) throws Exception {
        // Under "omit: [visibility]", the visibility symbol is empty: the name of the member
        // becomes the first character of the drawn line.
        UmlEntity box = new UmlEntity("Invoice", "", "", UMLVisibility.PUBLIC,
            List.of(new UmlField(name, "int", "", false)), List.of());

        String rendered = PlantUmlAssert.textRendered(box);

        assertTrue(rendered.contains(name),
            "The member \"" + name + "\" lost its first character:\n" + rendered);
    }

    @Test
    void protectedVisibility_staysTheHashPlantUmlExpects() {
        // The counterexample bounding the fix: the "#" of a protected member is written by
        // the library, and that is how PlantUML reads visibility. Encoding it would display
        // a literal hash instead of the UML notation.
        UmlEntity box = new UmlEntity("Invoice", "", "", UMLVisibility.PUBLIC,
            List.of(new UmlField("total", "int", "#", false)), List.of());

        assertTrue(PlantUmlAssert.documentFor(box).contains("  # total : int"),
            PlantUmlAssert.documentFor(box));
    }

    // --- Position 6: an enumeration literal ---

    @ParameterizedTest
    @ValueSource(strings = {"#Euro", "*Euro", "=Euro", "|Euro"})
    void enumerationLiteral_keepsItsFirstCharacter(String literal) throws Exception {
        // The position the audit report does not quote, and which suffers the same defect: a
        // literal takes its line by itself, with neither visibility nor type before it.
        UmlEntity currency = UmlEntity.builder("com.example.Currency", "Currency")
            .kind(UmlEntityKind.ENUM).literals(ImmutableList.of(literal, "Dollar")).build();

        String rendered = PlantUmlAssert.textRendered(currency);

        assertTrue(rendered.contains(literal),
            "The literal \"" + literal + "\" lost its first character:\n" + rendered);
    }

    // --- The encoder itself ---

    @Test
    void lineStart_encodesLineMarkersOnly() {
        // Always encoding the first character would have been enough to close the breach — that is what
        // noteBlock does, for another reason — but would have made the generated source unreadable,
        // which the project keeps precisely so that it can be read back and compared.
        assertEquals("Account bank", PlantUmlEscaper.lineStart("Account bank"));
        assertEquals("&#35; Attention", PlantUmlEscaper.lineStart("# Attention"));
        assertEquals("&#42; see", PlantUmlEscaper.lineStart("* see"));
        assertEquals("&#61; title", PlantUmlEscaper.lineStart("= title"));
        assertEquals("&#124;a|b|", PlantUmlEscaper.lineStart("|a|b|"));
    }

    @Test
    void lineStart_alsoDoesWhatLabelDoes() {
        // This is not a second grammar: it is label(), plus the neutralization of the first
        // character. The pairs stay broken, the angle brackets encoded.
        assertEquals("&#42;*bold&#42;*", PlantUmlEscaper.lineStart("**bold**"));
        assertEquals("&#60;b&#62;", PlantUmlEscaper.lineStart("<b>"));
        assertEquals("a\\nb", PlantUmlEscaper.lineStart("a\nb"));
    }

    @Test
    void lineStart_looksAtTheEscapedTextAndNotTheWrittenText() {
        // A control character is removed by escaping: it is the "#" that ends up
        // is found at the head of the drawn line, and that is what must be neutralized. Asking
        // asking the question of the original text would have concluded "not a marker".
        assertEquals("&#35;total", PlantUmlEscaper.lineStart("\1#total"));
    }

    @Test
    void lineStart_leavesEmptyATextEscapingEmpties() {
        assertEquals("", PlantUmlEscaper.lineStart("\1\2"));
        assertEquals("", PlantUmlEscaper.lineStart(""));
    }

    @Test
    void eachRiskyText_producesAValidDocument() {
        // The net: whatever the text, the source stays a source PlantUML accepts.
        for (String text : TEXTS_A_RISK) {
            PlantUmlAssert.assertCompile(PlantUmlAssert.documentFor(named(text)));
        }
    }
}
