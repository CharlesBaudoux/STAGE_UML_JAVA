package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// The rendering of constraints: UML's text between braces.
//
// Three positions, a single notation. What these tests check is that the braces are
// set by the generator — the user writes the bare rule — and that they land in the right
// place depending on what the constraint aims at.
class PlantUmlConstraintTest {

    private static UmlEntity box(List<UmlField> fields, List<String> constraints) {
        return UmlEntity.builder("com.example.Account", "Account")
            .fields(fields).constraints(constraints).build();
    }

    @Test
    void fieldConstraint_followsTheFieldLine() {
        String source = PlantUmlAssert.documentFor(box(
            List.of(new UmlField("balance", "double", "", "-", false, false,
                ImmutableList.of("always positive"))),
            List.of()));

        assertTrue(source.contains("- balance : double {always positive}"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void ruleContainingMarkup_isEscaped() {
        // A rule is free text: ">=" is common there, and the angle bracket must be encoded
        // as everywhere else, on pain of being read as markup.
        String source = PlantUmlAssert.documentFor(box(
            List.of(new UmlField("balance", "double", "", "-", false, false,
                ImmutableList.of("balance >= 0"))),
            List.of()));

        assertTrue(source.contains("{balance &#62;= 0}"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void severalConstraints_followEachOtherOnTheSameLine() {
        String source = PlantUmlAssert.documentFor(box(
            List.of(new UmlField("balance", "double", "", "-", false, false,
                ImmutableList.of("positive", "rounded to the cent"))),
            List.of()));

        assertTrue(source.contains("- balance : double {positive} {rounded to the cent}"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void constraintAndDerivation_coexist() {
        // The slash belongs to the name, the braces follow the whole field: the two
        // notations take distinct places and must not get in each other's way.
        String source = PlantUmlAssert.documentFor(box(
            List.of(new UmlField("age", "int", "", "-", false, true,
                ImmutableList.of("always positive"))),
            List.of()));

        assertTrue(source.contains("- /age : int {always positive}"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void classConstraint_isDrawnAsABubble() {
        // PlantUML has no notation of its own for a class constraint; UML writes it between
        // braces next to the box, which a note reproduces.
        String source = PlantUmlAssert.documentFor(box(List.of(),
            List.of("holder major")));

        assertEquals(List.of("note right of Account : {holder major}"),
            source.lines().filter(l -> l.startsWith("note ")).toList());
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void linkConstraint_joinsTheTargetEndpoint() {
        UmlAssociation association = UmlAssociation.builder("orders", "com.example.Order")
            .targetRole("purchases").targetMultiplicity("0..*")
            .constraints(List.of("subset")).build();

        String line = linkLine(association);

        assertTrue(line.contains("\"purchases 0..* {subset}\""), line);
    }

    @Test
    void severalConstraints_followEachOtherInWrittenOrder() {
        // "ordered" is a constraint like any other since the isOrdered parameter was
        // vanished: on the drawing, nothing tells it apart from a {subset} written by the author, and nothing
        // must tell it apart. It is therefore written where it was written.
        UmlAssociation association = UmlAssociation.builder("orders", "com.example.Order")
            .targetMultiplicity("0..*")
            .constraints(List.of("ordered", "subset")).build();

        assertTrue(linkLine(association).contains("\"0..* {ordered} {subset}\""),
            linkLine(association));
    }

    @Test
    void readerSeesConstraint() throws Exception {
        String text = PlantUmlAssert.textRendered(box(
            List.of(new UmlField("balance", "double", "", "-", false, false,
                ImmutableList.of("balance >= 0"))),
            List.of()));

        assertTrue(text.contains("{balance >= 0}"), text);
    }

    private static String linkLine(UmlAssociation association) {
        UmlEntity source = UmlEntity.builder("com.example.Client", "Client")
            .associations(ImmutableList.of(association)).build();
        UmlEntity target = UmlEntity.builder("com.example.Order", "Order").build();
        String document = PlantUmlAssert.documentFor(source, target);

        List<String> lines = PlantUmlAssert.linkLines(document);
        assertEquals(1, lines.size(), document);
        PlantUmlAssert.assertCompile(document);
        return lines.get(0);
    }
}
