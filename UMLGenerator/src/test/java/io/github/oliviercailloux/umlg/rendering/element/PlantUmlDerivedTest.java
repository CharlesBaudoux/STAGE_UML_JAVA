package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// The rendering of a derived element: UML's slash.
//
// What these tests watch above all is where the slash is placed. It belongs to the name of
// the property, and to it alone: neither the type, nor the visibility, nor the multiplicity carries it.
class PlantUmlDerivedTest {

    private static UmlEntity box(List<UmlField> fields, List<UmlMethod> methods) {
        return UmlEntity.builder("com.example.Person", "Person")
            .fields(fields).methods(methods).build();
    }

    @Test
    void derivedField_takesTheSlashBeforeItsName() {
        String source = PlantUmlAssert.documentFor(
            box(List.of(new UmlField("age", "int", "", "-", false, true, ImmutableList.of())),
                List.of()));

        assertTrue(source.contains("- /age : int"),
            "The slash must precede the name, and nothing else:\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void ordinaryField_takesNone() {
        String source = PlantUmlAssert.documentFor(
            box(List.of(new UmlField("balance", "double", "-", false)), List.of()));

        assertTrue(source.contains("- balance : double"), source);
        assertFalse(source.contains("/balance"), source);
    }

    @Test
    void derivedAndStaticField_keepsBothNotations() {
        // {static} belongs to PlantUML and sits before the type; the slash belongs to UML and
        // sits before the name. Both must coexist without stepping on each other.
        String source = PlantUmlAssert.documentFor(
            box(List.of(new UmlField("total", "double", "", "+", true, true, ImmutableList.of())),
                List.of()));

        assertTrue(source.contains("+ {static} /total : double"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void derivedMethod_takesTheSlashBeforeItsName() {
        String source = PlantUmlAssert.documentFor(box(List.of(),
            List.of(new UmlMethod("age", "int", "", "+", false, ImmutableList.of(), true))));

        assertTrue(source.contains("+ /age() : int"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void derivedLink_marksTheTargetRole() {
        UmlAssociation association = UmlAssociation.builder("orders", "com.example.Order")
            .targetRole("inProgress").targetMultiplicity("0..*").derived(true).build();

        String source = source(association);

        assertEquals(1, PlantUmlAssert.linkLines(source).size(), source);
        assertTrue(PlantUmlAssert.linkLines(source).get(0).contains("\"/inProgress 0..*\""),
            "The slash precedes the role, not the multiplicity:\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void derivedLinkWithoutRole_marksNothing() {
        // Nothing to name, hence nothing to mark. The rendering must above all not invent a holder
        // for the slash — the annotation processor, for its part, warns at compile time.
        UmlAssociation association = UmlAssociation.builder("orders", "com.example.Order")
            .targetMultiplicity("0..*").derived(true).build();

        String source = source(association);
        String link = PlantUmlAssert.linkLines(source).get(0);

        assertFalse(link.contains("/"), "No slash must appear on the stroke: " + link);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void readerSeesBar() throws Exception {
        // The "what the reader really sees" level: the slash must survive the drawing, and
        // not stay a subtlety of the source file.
        String text = PlantUmlAssert.textRendered(
            box(List.of(new UmlField("age", "int", "", "-", false, true, ImmutableList.of())),
                List.of()));

        assertTrue(text.contains("/age"), text);
    }

    private static String source(UmlAssociation association) {
        UmlEntity source = UmlEntity.builder("com.example.Client", "Client")
            .associations(ImmutableList.of(association)).build();
        UmlEntity target = UmlEntity.builder("com.example.Order", "Order").build();
        return PlantUmlAssert.documentFor(source, target);
    }
}
