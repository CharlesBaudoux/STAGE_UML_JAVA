package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// The choice between the two notations of an association: the stroke, or the line in the box.
//
// These tests all start from the annotations and go to the document, because that is precisely
// the path notation() diverts: the same @UMLAssociation must produce a stroke on one side,
// a field line on the other, and never both — the UML rule forbidding to display twice
// times the same property holds whatever the chosen notation.
class PlantUmlNotationTest {

    @UMLClass
    static class Order {}

    // The starting class, declined once per case. They look alike: it is the parameter
    // that changes, and it alone.

    @UMLClass(name = "Default")
    static class Default {
        @UMLAssociation(target = Order.class, targetMultiplicity = "0..*")
        private List<Order> orders;
    }

    @UMLClass(name = "InBox")
    static class InBox {
        @UMLAssociation(target = Order.class, targetMultiplicity = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Order> orders;
    }

    private static String document(Class<?>... classes) {
        UmlEntity[] entities = new UmlEntity[classes.length];
        for (int i = 0; i < classes.length; i++) {
            entities[i] = EntityExtractor.extract(classes[i]);
        }
        return PlantUmlAssert.documentFor(entities);
    }

    // --- The default ---

    @Test
    void withoutNotation_theLinkStaysAStroke() {
        String source = document(Default.class, Order.class);

        assertEquals(List.of("Default --> \"0..*\" Order"), PlantUmlAssert.linkLines(source));
        assertFalse(source.contains("orders"),
            "The field left the box in favour of the stroke:\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    // --- La notation ATTRIBUTE ---

    @Test
    void attributeNotation_writesTheLineInTheBox() {
        String source = document(InBox.class, Order.class);

        // The displayed type is the target, and not "List": it is the multiplicity that says there
        // has several, as UML requires.
        assertTrue(source.contains("- orders : Order [0..*]"),
            "La line expected is missing :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void attributeNotation_drawsNoStroke() {
        String source = document(InBox.class, Order.class);

        assertEquals(List.of(), PlantUmlAssert.linkLines(source),
            "A property is never displayed twice:\n" + source);
        // Both boxes stay: the target does not vanish with the stroke.
        assertEquals(2, PlantUmlAssert.aliasDeclared(source).size(), source);
    }

    @Test
    void readerSeesMultiplicity() throws Exception {
        String text = PlantUmlAssert.textRendered(EntityExtractor.extract(InBox.class),
            EntityExtractor.extract(Order.class));

        assertTrue(text.contains("[0..*]"),
            "The multiplicity must survive the drawing, and not stay in the source file:\n"
                + text);
    }

    @UMLClass
    static class WithoutMultiplicity {
        @UMLAssociation(target = Order.class, notation = UMLNotation.ATTRIBUTE)
        private Order order;
    }

    @Test
    void withoutMultiplicity_noBracketIsWritten() {
        String source = document(WithoutMultiplicity.class, Order.class);

        assertTrue(source.contains("- order : Order\n"),
            "An empty multiplicity means \"exactly one\" and is not written:\n" + source);
        assertFalse(source.contains("[]"), source);
    }

    // --- What the line carries on top ---

    @UMLClass
    static class Decorated {
        @UMLConstraint(rule = "ordered")
        @UMLConstraint(rule = "not empty")
        @UMLDerived
        @UMLAssociation(target = Order.class, targetMultiplicity = "1..*",
            notation = UMLNotation.ATTRIBUTE)
        protected List<Order> purchases;
    }

    @Test
    void slashMultiplicityAndConstraints_followTheUmlOrder() {
        // The order is UML's, and the same as on a stroke: the name, its multiplicity, then
        // the rules, in the order they are written.
        String source = document(Decorated.class, Order.class);

        assertTrue(source.contains("# /purchases : Order [1..*] {ordered} {not empty}"),
            "La line expected is missing :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    // --- The role, which becomes the name of the property ---

    @UMLClass(name = "Keyboard")
    static class Keyboard {
        @UMLNote(text = "Doublons autorises")
        @UMLAssociation(target = Order.class, targetRole = "physicalKeys",
            targetMultiplicity = "0..*", notation = UMLNotation.ATTRIBUTE)
        private List<Order> keys;
    }

    @Test
    void targetRole_namesTheProperty() {
        // In attribute notation, the target role is the name one reads in the box: that is what
        // what UML says, and that is what hand-kept diagrams write — "physicalKeys"
        // where the code declares a "keys" field.
        String source = document(Keyboard.class, Order.class);

        assertTrue(source.contains("- physicalKeys : Order [0..*]"),
            "The role must name the line:\n" + source);
        assertFalse(source.contains(" keys "), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteOnAProperty_hangsOnTheDisplayedName() {
        // A bubble designates a member by the name PlantUML knows it under; aiming at the name of the
        // a Java field would hang the note on a member that does not exist.
        String source = document(Keyboard.class, Order.class);

        assertTrue(source.contains("note right of Keyboard::physicalKeys"),
            "The note must aim at the displayed name:\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    // --- Exemptions and the strategy ---

    @UMLClass(strategy = UMLVisibility.NONE)
    static class SilentStrategy {
        @UMLAssociation(target = Order.class, targetMultiplicity = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Order> orders;
    }

    @Test
    void noneStrategy_doesNotHideAnAskedForAssociation() {
        // Under the ARROW notation, the stroke is drawn whatever the strategy: an explicit
        // explicit wins over a default. The ATTRIBUTE notation follows the same rule, otherwise
        // the same code would say two things depending on how it is drawn.
        String source = document(SilentStrategy.class, Order.class);

        assertTrue(source.contains("- orders : Order [0..*]"), source);
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Removed {
        @UMLExclude
        @UMLAssociation(target = Order.class, targetMultiplicity = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Order> orders;
    }

    @Test
    void exclude_removesTheLineAsItWouldRemoveTheStroke() {
        String source = document(Removed.class, Order.class);

        assertFalse(source.contains("orders"), source);
        assertEquals(List.of(), PlantUmlAssert.linkLines(source), source);
    }

    // --- What the notation does not loosen ---
    //
    // These fixtures deliberately carry no @UMLClass, and member extraction is
    // called directly: the sweep of target/test-classes by the integration tests
    // would otherwise extract, and would fail on faults that are here the object of the test.

    static class OutsideDiagram {}

    static class TargetNotAnnotated {
        @UMLAssociation(target = OutsideDiagram.class, notation = UMLNotation.ATTRIBUTE)
        private OutsideDiagram field;
    }

    @Test
    void targetWithoutUmlClass_isRefusedAsForAStroke() {
        // The notation picks a drawing form; it does not make just any class
        // a class of the diagram.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(TargetNotAnnotated.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("@UMLClass"), e.getMessage());
    }

    static class MultiplicityFaulty {
        @UMLAssociation(target = Order.class, targetMultiplicity = "3..1",
            notation = UMLNotation.ATTRIBUTE)
        private List<Order> orders;
    }

    @Test
    void invalidMultiplicity_isRefusedAndNamesTheField() {
        // The same grammar as on a stroke, and the same refusal: "3..1" describes nothing.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(MultiplicityFaulty.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("orders"),
            "The message must name the faulty field: " + e.getMessage());
    }

    // --- Both notations in the same diagram ---

    @UMLClass
    static class Mixed {
        @UMLAssociation(target = Order.class, targetMultiplicity = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Order> archived;

        @UMLAssociation(target = Order.class, targetMultiplicity = "1", targetRole = "current")
        private Order current;
    }

    @Test
    void twoNotations_coexistOnTheSameClass() {
        String source = document(Mixed.class, Order.class);

        assertTrue(source.contains("- archived : Order [0..*]"), source);
        assertEquals(List.of("Mixed --> \"current 1\" Order"),
            PlantUmlAssert.linkLines(source), source);
        PlantUmlAssert.assertCompile(source);
    }
}
