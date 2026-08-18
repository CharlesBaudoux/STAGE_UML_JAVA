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

class PlantUmlLineStartTest {

    private static final List<String> TEXTS_A_RISK = List.of(
        "# Warning", "* see the contract", "= title", "|a|b|");

    private static UmlEntity named(String name) {
        return new UmlEntity(name, "", "", UMLVisibility.PUBLIC, List.of(), List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"# Warning", "* see the contract", "= title", "|a|b|"})
    void boxName_keepsItsFirstCharacter(String name) throws Exception {
        String rendered = PlantUmlAssert.textRendered(named(name));

        assertTrue(rendered.contains(name),
            "The name \"" + name + "\" was read as markup. Drawn text:\n" + rendered);
    }

    @ParameterizedTest
    @ValueSource(strings = {"# Warning", "* see the contract", "= title", "|a|b|"})
    void groupName_keepsItsFirstCharacter(String name) throws Exception {
        UmlEntity filed = UmlEntity.builder("com.example.Invoice", "Invoice")
            .group(new UmlPackage(name, "")).build();

        String rendered = PlantUmlAssert.textRendered(filed);

        assertTrue(rendered.contains(name),
            "The group name \"" + name + "\" was read as markup:\n" + rendered);
    }

    @Test
    void diagramTitle_keepsItsEqualSigns() throws Exception {
        UmlDiagram settings = new UmlDiagram("=== Billing ===", UMLFlow.TOP_TO_BOTTOM, true);
        String source =
            PlantUmlAssert.documentFor(settings, named("Invoice"));

        PlantUmlAssert.assertCompile(source);
        assertTrue(source.contains("title &#61;== Billing ==="),
            "Only the first character is encoded, and that is enough:\n" + source);
    }

    @ParameterizedTest
    @ValueSource(strings = {"# Warning", "* see the contract", "= title", "|a|b|"})
    void classNote_keepsItsFirstCharacter(String text) throws Exception {
        UmlEntity withNote = UmlEntity.builder("com.example.Invoice", "Invoice")
            .notes(ImmutableList.of(UmlNote.onClass(text, UMLDirection.RIGHT))).build();

        String rendered = PlantUmlAssert.textRendered(withNote);

        assertTrue(rendered.contains(text),
            "The note \"" + text + "\" was read as markup:\n" + rendered);
    }

    @ParameterizedTest
    @ValueSource(strings = {"#total", "*total", "=total", "|total"})
    void memberWithoutVisibility_keepsItsFirstCharacter(String name) throws Exception {
        UmlEntity box = new UmlEntity("Invoice", "", "", UMLVisibility.PUBLIC,
            List.of(new UmlField(name, "int", "", false)), List.of());

        String rendered = PlantUmlAssert.textRendered(box);

        assertTrue(rendered.contains(name),
            "The member \"" + name + "\" lost its first character:\n" + rendered);
    }

    @Test
    void protectedVisibility_staysTheHashPlantUmlExpects() {
        UmlEntity box = new UmlEntity("Invoice", "", "", UMLVisibility.PUBLIC,
            List.of(new UmlField("total", "int", "#", false)), List.of());

        assertTrue(PlantUmlAssert.documentFor(box).contains("  # total : int"),
            PlantUmlAssert.documentFor(box));
    }

    @ParameterizedTest
    @ValueSource(strings = {"#Euro", "*Euro", "=Euro", "|Euro"})
    void enumerationLiteral_keepsItsFirstCharacter(String literal) throws Exception {
        UmlEntity currency = UmlEntity.builder("com.example.Currency", "Currency")
            .kind(UmlEntityKind.ENUM).literals(ImmutableList.of(literal, "Dollar")).build();

        String rendered = PlantUmlAssert.textRendered(currency);

        assertTrue(rendered.contains(literal),
            "The literal \"" + literal + "\" lost its first character:\n" + rendered);
    }

    @Test
    void lineStart_encodesLineMarkersOnly() {
        assertEquals("Account bank", PlantUmlEscaper.lineStart("Account bank"));
        assertEquals("&#35; Attention", PlantUmlEscaper.lineStart("# Attention"));
        assertEquals("&#42; see", PlantUmlEscaper.lineStart("* see"));
        assertEquals("&#61; title", PlantUmlEscaper.lineStart("= title"));
        assertEquals("&#124;a|b|", PlantUmlEscaper.lineStart("|a|b|"));
    }

    @Test
    void lineStart_alsoDoesWhatLabelDoes() {
        assertEquals("&#42;*bold&#42;*", PlantUmlEscaper.lineStart("**bold**"));
        assertEquals("&#60;b&#62;", PlantUmlEscaper.lineStart("<b>"));
        assertEquals("a\\nb", PlantUmlEscaper.lineStart("a\nb"));
    }

    @Test
    void lineStart_looksAtTheEscapedTextAndNotTheWrittenText() {
        assertEquals("&#35;total", PlantUmlEscaper.lineStart("\1#total"));
    }

    @Test
    void lineStart_leavesEmptyATextEscapingEmpties() {
        assertEquals("", PlantUmlEscaper.lineStart("\1\2"));
        assertEquals("", PlantUmlEscaper.lineStart(""));
    }

    @Test
    void eachRiskyText_producesAValidDocument() {
        for (String text : TEXTS_A_RISK) {
            PlantUmlAssert.assertCompile(PlantUmlAssert.documentFor(named(text)));
        }
    }
}
