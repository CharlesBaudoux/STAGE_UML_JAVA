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

class PlantUmlEscapeTest {

    private static UmlEntity named(String name) {
        return new UmlEntity(name, "", "", UMLVisibility.PUBLIC, List.of(), List.of());
    }

    private static UmlEntity stereotyped(String stereotype) {
        return new UmlEntity("E", stereotype, "", UMLVisibility.PUBLIC, List.of(), List.of());
    }

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
            "Account [[http://elsewhere.example]]",
            "[[http://elsewhere.example{infobulle} label]]",
            "**bold**",
            "//slope//",
            "~~bar~~",
            "__underscore__",
            "--bar--",
            "with null \u0000 and bip \u0007");
    }

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

        assertEquals(2, declaration.chars().filter(c -> c == '"').count(),
            "The name introduced a quote in the declaration: " + declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Account (bank)", "The User", "Client<T>", "Name\"Injected", "A&B",
        "&#60;b&#62;", "C:\\Users", "Reservation n°42",
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
        assertEquals("a\\b", PlantUmlEscaper.stereotype("a\\b"));
        assertEquals("a\\\\b", PlantUmlEscaper.label("a\\b"));
    }

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

    @Test
    void encoder_usesNumericEntitiesOnly() {
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

    @Test
    void aNameCarryingALink_producesNoHref() throws Exception {
        String svg = PlantUmlAssert.svg(named("Account [[http://elsewhere.example]]"));

        assertFalse(svg.contains("elsewhere.example\""),
            "The name generated a link in the SVG:\n" + svg);
        assertFalse(svg.contains("<a href="), "The name generated a link in the SVG:\n" + svg);
    }

    @Test
    void encoder_breaksThePairOnly_andLeavesTheLoneCharacter() {
        assertEquals("field_name", PlantUmlEscaper.label("field_name"));
        assertEquals("a * b", PlantUmlEscaper.label("a * b"));
        assertEquals("and/or", PlantUmlEscaper.label("and/or"));

        assertEquals("&#95;_underscore&#95;_", PlantUmlEscaper.label("__underscore__"));
        assertEquals("&#91;[link]]", PlantUmlEscaper.label("[[link]]"));
    }

    @Test
    void stereotypeEmptyAfterEscaping_writesNoEmptyAngleBrackets() {
        String source = PlantUmlAssert.documentFor(stereotyped("\1\2"));

        assertFalse(source.contains("<<>>"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void nameEmptyAfterEscaping_showsInTheGeneratedSource() {
        assertEquals("", PlantUmlEscaper.label("\1\2"));
        assertEquals("", PlantUmlEscaper.stereotype("\1\2"));
    }

    @Test
    void nameEmptyAfterEscaping_isRefusedByTheRendering() {
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> PlantUmlAssert.documentFor(named("\1\2")));

        assertTrue(refusal.getMessage().contains("invisible characters only"), refusal.getMessage());
    }

    @Test
    void encoder_appliedTwice_stillProducesNoTag() {
        String twoTimes = PlantUmlEscaper.label(PlantUmlEscaper.label("<img:/etc/hostname>"));

        assertFalse(twoTimes.contains("<"), twoTimes);
    }
}
