package io.github.oliviercailloux.umlg.rendering.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

// Escaping, exercised on deliberately hostile texts.
//
// Two opposite requirements, to be held at the same time:
//   - safety — no text must be able to declare a class, open a second diagram nor
//     activate a Creole tag (<img>, <size>, <b>);
//   - fidelity — what the user wrote must be displayed as is, quotes and
//     esperluettes compris.
//
// Fidelity is checked on the rendered SVG (textRendered), not on the source: an encoded text
// in the source is precisely what is expected, and it becomes readable again on screen.
class PlantUmlEscapeTest {

    private static UmlEntity named(String name) {
        return new UmlEntity(name, "", "", UMLVisibility.PUBLIC, List.of(), List.of());
    }

    private static UmlEntity stereotyped(String stereotype) {
        return new UmlEntity("E", stereotype, "", UMLVisibility.PUBLIC, List.of(), List.of());
    }

    // The catalogue of attacks, supplied to the tests by @MethodSource — the counterpart of @ValueSource
    // when the values need to be built. Each entry aims at a precise breach:
    // escaping the quotes, injecting a tag, doubling the encoding, breaking the line, closing
    // the diagram, slipping in control characters.
    static List<String> namesHostile() {
        return List.of(
            "Name\"Injected",
            "\" as X\nclass \"Intruse",
            "<img:/etc/hostname>",
            "<size:40>ENORME</size>",
            "<b>gras</b>",
            "&#60;img:/etc/hostname&#62;",
            "C:\\Users\\moi",
            "end\\",
            "line1\nline2",
            "line1\r\nline2",
            "@enduml\n@startuml\nclass Intruse",
            "}\nclass Intruse {",
            // The markup PlantUML reads on top of the angle brackets: Creole, and the syntax of
            // link. These entries were missing from the catalogue, and they all got through — "[[…]]"
            // generated an href in the SVG, the others swallowed their own markers.
            "Account [[http://elsewhere.example]]",
            "[[http://elsewhere.example{infobulle} label]]",
            "**bold**",
            "//slope//",
            "~~bar~~",
            "__underscore__",
            "--bar--",
            "with null \u0000 and bip \u0007");
    }

    // --- Main invariant: no entry can break the document ---

    @ParameterizedTest
    @MethodSource("namesHostile")
    void hostileName_producesAValidSingleClassDocument(String name) {
        String source = PlantUmlAssert.documentFor(named(name));

        PlantUmlAssert.assertCompile(source);
        assertEquals(1, PlantUmlAssert.namesDeclared(source).size(),
            "The hostile name declared an extra class:\n" + source);
        assertEquals(1, PlantUmlAssert.diagramsCount(source),
            "A single diagram must be opened:\n" + source);
    }

    @ParameterizedTest
    @MethodSource("namesHostile")
    void hostileName_letsNoCreoleTagThrough(String name) {
        String source = PlantUmlAssert.documentFor(named(name));

        assertFalse(source.contains("<img"), "Tag <img> not neutralized:\n" + source);
        assertFalse(source.contains("<size"), "Tag <size> not neutralized:\n" + source);
        assertFalse(source.contains("<b>"), "Tag <b> not neutralized:\n" + source);
    }

    @ParameterizedTest
    @MethodSource("namesHostile")
    void hostileName_neverBreaksTheDeclarationLine(String name) {
        String source = PlantUmlAssert.documentFor(named(name));
        String declaration = source.lines()
            .filter(l -> l.startsWith("class "))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No declaration:\n" + source));

        // Exactly two quotes: the ones the writer set itself.
        assertEquals(2, declaration.chars().filter(c -> c == '"').count(),
            "The name introduced a quote in the declaration: " + declaration);
    }

    // --- Fidelity: what is displayed is what the user wrote ---

    @ParameterizedTest
    @ValueSource(strings = {
        "Account (bank)", "The User", "Client<T>", "Name\"Injected", "A&B",
        "&#60;b&#62;", "C:\\Users", "Reservation n°42",
        // Creole was consumed leaving no trace: "**Account**" was displayed
        // "Account" in bold, and the asterisks the user had written were missing.
        "**bold**", "//slope//", "~~bar~~", "__underscore__", "--bar--",
        "Account [[http://elsewhere.example]]",
    })
    void anyName_isDisplayedAsIs(String name) throws Exception {
        String rendered = PlantUmlAssert.textRendered(named(name));

        assertTrue(rendered.contains(name),
            "The name \"" + name + "\" is not rendered faithfully. Drawn text:\n" + rendered);
    }

    @Test
    void escapedEntity_isNotReinterpreted() throws Exception {
        // The classic workaround: encoding the tag oneself to cross an
        // an escaping that would have forgotten the ampersand.
        UmlEntity e = named("&#60;img:/etc/hostname&#62;");

        String source = PlantUmlAssert.documentFor(e);
        assertTrue(source.contains("&#38;#60;"),
            "The ampersand must be encoded first: " + source);

        String rendered = PlantUmlAssert.textRendered(e);
        assertTrue(rendered.contains("&#60;img:/etc/hostname&#62;"),
            "The text must be displayed literally, not decoded into a tag:\n" + rendered);
    }

    @Test
    void backslash_isDoubledAndNotEncoded() throws Exception {
        // "&#92;" crashes PlantUML (the decoded backslash is fed back into a
        // Matcher.appendReplacement): "\\" is the right escape.
        String source = PlantUmlAssert.documentFor(named("C:\\Users"));

        assertFalse(source.contains("&#92;"), source);
        assertTrue(source.contains("C:\\\\Users"), source);
        assertTrue(PlantUmlAssert.textRendered(named("C:\\Users")).contains("C:\\Users"));
    }

    @Test
    void lineBreak_becomesALineFeed() {
        String source = PlantUmlAssert.documentFor(named("line1\nline2"));

        assertEquals(1, source.lines().filter(l -> l.startsWith("class ")).count());
        assertTrue(source.contains("line1\\nline2"),
            "The line break must become a PlantUML \"\\n\", not vanish:\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void windowsCarriageReturn_producesASingleLineBreak() {
        String source = PlantUmlAssert.documentFor(named("line1\r\nline2"));

        assertTrue(source.contains("line1\\nline2"), source);
        assertFalse(source.contains("\\n\\n"), source);
    }

    // --- Stereotype: another context, other rules ---

    @Test
    void stereotypeWithAngleBrackets_doesNotTruncateWhatFollows() throws Exception {
        UmlEntity e = stereotyped("A>>B");

        PlantUmlAssert.assertCompile(PlantUmlAssert.documentFor(e));
        assertTrue(PlantUmlAssert.textRendered(e).contains("A>>B"),
            "An inner \">>\" silently truncated the stereotype.");
    }

    @Test
    void stereotypeWithAmpersand_isDisplayed() throws Exception {
        assertTrue(PlantUmlAssert.textRendered(stereotyped("R&D")).contains("R&D"));
    }

    @Test
    void stereotypeWithLineBreak_staysOnOneLine() {
        String source = PlantUmlAssert.documentFor(stereotyped("a\nb"));

        assertTrue(source.contains("<<a b>>"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void stereotype_doesNotEncodeTheBackslash() {
        // Unlike the label, the content of << >> is not passed to Creole: doubling
        // l'antislash en afficherait two.
        assertEquals("a\\b", PlantUmlEscaper.stereotype("a\\b"));
        assertEquals("a\\\\b", PlantUmlEscaper.label("a\\b"));
    }

    // --- Members ---

    @Test
    void hostileMember_doesNotBreakTheCompartment() {
        UmlEntity e = new UmlEntity("E", "", "", UMLVisibility.PUBLIC,
            List.of(new UmlField("field\n}\nclass Intruse {", "<img:/etc/hostname>", "-", false)),
            List.of(new UmlMethod("m\"ethode", "void", "+", false, List.of("A>B"))));

        String source = PlantUmlAssert.documentFor(e);

        PlantUmlAssert.assertCompile(source);
        assertEquals(1, PlantUmlAssert.namesDeclared(source).size(), source);
        assertFalse(source.contains("<img"), source);
    }

    // --- Colour: a closed vocabulary, hence validated and not encoded ---

    @Test
    void namedColour_isAccepted() {
        String source = PlantUmlAssert.documentFor(
            new UmlEntity("E", "", "LightBlue", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertTrue(source.contains("#LightBlue"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void unknownColour_failsPlainly() {
        UmlEntity e = new UmlEntity("E", "", "NotAColor", UMLVisibility.PUBLIC,
            List.of(), List.of());

        // Without this guard, PlantUML would return an error image no file-size test
        // file is told from a correct diagram.
        IllegalArgumentException error =
            assertThrows(IllegalArgumentException.class, () -> PlantUmlAssert.documentFor(e));
        assertTrue(error.getMessage().contains("NotAColor"), error.getMessage());
    }

    @Test
    void hexadecimalColour_staysAccepted() {
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentFor(
            new UmlEntity("E", "", "AABBCC", UMLVisibility.PUBLIC, List.of(), List.of())));
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentFor(
            new UmlEntity("E", "", "#ABC", UMLVisibility.PUBLIC, List.of(), List.of())));
    }

    // --- The encoder itself ---

    @Test
    void encoder_usesNumericEntitiesOnly() {
        // PlantUML does not decode named entities: "&lt;" would be displayed as is.
        assertEquals("&#60;&#62;&#38;&#34;", PlantUmlEscaper.label("<>&\""));
    }

    @Test
    void encoder_leavesAnOrdinaryTextIntact() {
        String text = "Account (bank) n°42 - The User";

        assertEquals(text, PlantUmlEscaper.label(text));
        assertEquals(text, PlantUmlEscaper.stereotype(text));
    }

    @Test
    void encoder_removesControlCharacters() {
        assertEquals("ab", PlantUmlEscaper.label("a\u0000b"));
        assertEquals("ab", PlantUmlEscaper.stereotype("a\u0007b"));
    }

    // --- Creole markup, and the link syntax ---

    @Test
    void aNameCarryingALink_producesNoHref() throws Exception {
        // The breach: PlantUML reads "[[url]]" as a link, and escaping knew
        // than the angle brackets. The produced SVG carried an href to an arbitrary address — and the
        // box carried two when the library already set its navigation link there.
        String svg = PlantUmlAssert.svg(named("Account [[http://elsewhere.example]]"));

        assertFalse(svg.contains("elsewhere.example\""),
            "The name generated a link in the SVG:\n" + svg);
        assertFalse(svg.contains("<a href="), "The name generated a link in the SVG:\n" + svg);
    }

    @Test
    void encoder_breaksThePairOnly_andLeavesTheLoneCharacter() {
        // Encoding every occurrence would have been enough to close the breach, but would have made the
        // the generated file unreadable: a lone underscore or asterisk is an
        // ordinary, present in plenty of identifiers. Only the pair is broken.
        assertEquals("field_name", PlantUmlEscaper.label("field_name"));
        assertEquals("a * b", PlantUmlEscaper.label("a * b"));
        assertEquals("and/or", PlantUmlEscaper.label("and/or"));

        // Both pairs are broken, the opening and the closing one: PlantUML does not require
        // that they be paired to recognize a marker.
        assertEquals("&#95;_underscore&#95;_", PlantUmlEscaper.label("__underscore__"));
        // The closing bracket opens nothing: only "[[" generates a link.
        assertEquals("&#91;[link]]", PlantUmlEscaper.label("[[link]]"));
    }

    // --- When escaping empties the string ---
    //
    // isBlank() does not treat control characters as blanks, whereas escaping
    // removes them: a text made of those characters alone crossed every upstream guard,
    // then came back out as "". The empty string is faulty at three positions — "class \"\" as C"
    // and "<<>>" are refused by the engine, "<i></i>" draws as "</i>" on the stroke —, and
    // so the question must be asked of the escaped text.

    // Refusal at extraction time is exercised by ClassHeaderValidatorTest, which reaches the class
    // validation; here the belt is exercised, on the rendering side — a UmlEntity can be built by
    // hand, without going through extraction, and "<<>>" would stay refused by the engine.
    @Test
    void stereotypeEmptyAfterEscaping_writesNoEmptyAngleBrackets() {
        String source = PlantUmlAssert.documentFor(stereotyped("\1\2"));

        assertFalse(source.contains("<<>>"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void nameEmptyAfterEscaping_showsInTheGeneratedSource() {
        // The demonstration of the defect, at the position the upstream validation checks: without the
        // ClassHeaderValidator's refusal, it is "class \"\" as C" that would be written, and the engine
        // rejects it.
        assertEquals("", PlantUmlEscaper.label("\1\2"));
        assertEquals("", PlantUmlEscaper.stereotype("\1\2"));
    }

    @Test
    void nameEmptyAfterEscaping_isRefusedByTheRendering() {
        // The rendering's belt, symmetrical to the stereotype's: the model refuses an empty name
        // or made of spaces, but it does not know PlantUML and therefore lets through a name made of
        // control characters. The message names the box, where the engine's refusal, two
        // layers further on, blamed "a defect of the library".
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> PlantUmlAssert.documentFor(named("\1\2")));

        assertTrue(refusal.getMessage().contains("invisible characters only"), refusal.getMessage());
    }

    @Test
    void encoder_appliedTwice_stillProducesNoTag() {
        // Encoding twice is not the identity (that is expected), but must never reopen
        // a tag: it is the property that counts.
        String twoTimes = PlantUmlEscaper.label(PlantUmlEscaper.label("<img:/etc/hostname>"));

        assertFalse(twoTimes.contains("<"), twoTimes);
    }
}
