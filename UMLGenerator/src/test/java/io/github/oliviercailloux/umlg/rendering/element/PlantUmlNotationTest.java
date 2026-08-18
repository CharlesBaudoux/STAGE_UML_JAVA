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

class PlantUmlNotationTest {

    @UMLClass
    static class Order {}

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

    @Test
    void withoutNotation_theLinkStaysAStroke() {
        String source = document(Default.class, Order.class);

        assertEquals(List.of("Default --> \"0..*\" Order"), PlantUmlAssert.linkLines(source));
        assertFalse(source.contains("orders"),
            "The field left the box in favour of the stroke:\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void attributeNotation_writesTheLineInTheBox() {
        String source = document(InBox.class, Order.class);

        assertTrue(source.contains("- orders : Order [0..*]"),
            "La line expected is missing :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void attributeNotation_drawsNoStroke() {
        String source = document(InBox.class, Order.class);

        assertEquals(List.of(), PlantUmlAssert.linkLines(source),
            "A property is never displayed twice:\n" + source);
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
        String source = document(Decorated.class, Order.class);

        assertTrue(source.contains("# /purchases : Order [1..*] {ordered} {not empty}"),
            "La line expected is missing :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @UMLClass(name = "Keyboard")
    static class Keyboard {
        @UMLNote(text = "Doublons autorises")
        @UMLAssociation(target = Order.class, targetRole = "physicalKeys",
            targetMultiplicity = "0..*", notation = UMLNotation.ATTRIBUTE)
        private List<Order> keys;
    }

    @Test
    void targetRole_namesTheProperty() {
        String source = document(Keyboard.class, Order.class);

        assertTrue(source.contains("- physicalKeys : Order [0..*]"),
            "The role must name the line:\n" + source);
        assertFalse(source.contains(" keys "), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteOnAProperty_hangsOnTheDisplayedName() {
        String source = document(Keyboard.class, Order.class);

        assertTrue(source.contains("note right of Keyboard::physicalKeys"),
            "The note must aim at the displayed name:\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @UMLClass(strategy = UMLVisibility.NONE)
    static class SilentStrategy {
        @UMLAssociation(target = Order.class, targetMultiplicity = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Order> orders;
    }

    @Test
    void noneStrategy_doesNotHideAnAskedForAssociation() {
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

    static class OutsideDiagram {}

    static class TargetNotAnnotated {
        @UMLAssociation(target = OutsideDiagram.class, notation = UMLNotation.ATTRIBUTE)
        private OutsideDiagram field;
    }

    @Test
    void targetWithoutUmlClass_isRefusedAsForAStroke() {
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
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(MultiplicityFaulty.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("orders"),
            "The message must name the faulty field: " + e.getMessage());
    }

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
