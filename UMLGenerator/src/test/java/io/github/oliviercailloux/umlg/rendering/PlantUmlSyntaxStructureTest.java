package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantUmlSyntaxStructureTest {

    private static UmlEntity entity(List<UmlField> fields, List<UmlMethod> methods) {
        return new UmlEntity("E", "", "", UMLVisibility.PUBLIC, fields, methods);
    }

    private static final UmlField Field = new UmlField("name", "String", "-", false);
    private static final UmlMethod Method = new UmlMethod("agir", "void", "+", false, List.of());

    private static String rendered(UmlEntity entity) {
        return PlantUmlAssert.documentFor(entity);
    }

    @Test
    void separator_presentOnlyIfFieldsAndMethods() {
        String source = rendered(entity(List.of(Field), List.of(Method)));

        assertTrue(source.contains("  --\n"),
            "The separator must appear between fields and methods.");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void separator_absentIfFieldsOnly() {
        String source = rendered(entity(List.of(Field), List.of()));

        assertFalse(source.contains("--"),
            "With no method, a separator would leave an empty compartment in the rendering.");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void separator_absentIfMethodsOnly() {
        String source = rendered(entity(List.of(), List.of(Method)));

        assertFalse(source.contains("--"));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void entityWithoutAnyMember_producesAValidShell() {
        String source = rendered(entity(List.of(), List.of()));

        assertFalse(source.contains("--"));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void field_isRenderedTypeThenName() {
        String source = rendered(entity(List.of(new UmlField("age", "int", "+", false)), List.of()));

        assertTrue(source.contains("  + age : int\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void staticField_carriesTheStaticMarker() {
        String source = rendered(entity(List.of(new UmlField("Threshold", "int", "+", true)), List.of()));

        assertTrue(source.contains("  + {static} Threshold : int\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void theFourVisibilitySymbols_areRenderedAsIs() {
        String source = rendered(entity(List.of(
            new UmlField("a", "int", "+", false),
            new UmlField("b", "int", "-", false),
            new UmlField("c", "int", "#", false),
            new UmlField("d", "int", "~", false)), List.of()));

        assertTrue(source.contains("+ a : int"), source);
        assertTrue(source.contains("- b : int"), source);
        assertTrue(source.contains("# c : int"), source);
        assertTrue(source.contains("~ d : int"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void methodWithoutParameter_renderedWithEmptyParentheses() {
        String source = rendered(entity(List.of(), List.of(Method)));

        assertTrue(source.contains("  + agir() : void\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void parameters_areJoinedByCommaSpace() {
        UmlMethod m = new UmlMethod("compute", "double", "+", false,
            List.of("int", "String", "boolean"));
        String source = rendered(entity(List.of(), List.of(m)));

        assertTrue(source.contains("  + compute(int, String, boolean) : double\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void staticMethod_carriesTheStaticMarkerBeforeTheReturnType() {
        UmlMethod m = new UmlMethod("creer", "E", "+", true, List.of());
        String source = rendered(entity(List.of(), List.of(m)));

        assertTrue(source.contains("  + {static} creer() : E\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void genericType_doesNotBreakTheSyntax() {
        UmlField field = new UmlField("elements", "String[]", "-", false);
        String source = rendered(entity(List.of(field), List.of()));

        assertTrue(source.contains("- elements : String[]"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void emptyStereotype_isNotRendered() {
        String source =
            rendered(new UmlEntity("E", "", "", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertFalse(source.contains("<<"), "An empty stereotype must not produce \"<<>>\".");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void filledInStereotype_isRenderedBetweenAngleBrackets() {
        String source =
            rendered(new UmlEntity("E", "Service", "", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertTrue(source.contains("<<Service>>"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void colourWithoutHash_isNormalizedAsWithHash() {
        String without =
            rendered(new UmlEntity("E", "", "AABBCC", UMLVisibility.PUBLIC, List.of(), List.of()));
        String with =
            rendered(new UmlEntity("E", "", "#AABBCC", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertEquals(with, without, "\u00ab AABBCC \u00bb and \u00ab #AABBCC \u00bb must produce an identical rendering.");
        assertTrue(without.contains("#AABBCC"), without);
        PlantUmlAssert.assertCompile(without);
    }

    @Test
    void emptyColour_isNotRendered() {
        String source =
            rendered(new UmlEntity("E", "", "", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertTrue(source.contains("class \"E\" as E {"),
            () -> "An empty colour must not produce an orphan \"#\":\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void stereotypeAndColour_coexistInTheDeclaration() {
        String source = rendered(
            new UmlEntity("E", "Entity", "#FF0000", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertTrue(source.contains("class \"E\" as E <<Entity>> #FF0000 {"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void wholeDocument_opensAndClosesTheDiagram() {
        String source = rendered(entity(List.of(), List.of()));

        assertTrue(source.startsWith("@startuml\n"), source);
        assertTrue(source.endsWith("@enduml\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void severalClasses_areAllDeclared() {
        UmlEntity a = new UmlEntity("A", "", "", UMLVisibility.PUBLIC, List.of(), List.of());
        UmlEntity b = new UmlEntity("B", "", "", UMLVisibility.PUBLIC, List.of(), List.of());

        String source = PlantUmlAssert.documentFor(a, b);

        assertEquals(List.of("A", "B"), PlantUmlAssert.namesDeclared(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void addClass_isFluent() {
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder(UmlStyle.PLANTUML);
        UmlEntity e = entity(List.of(), List.of());

        assertSame(builder, builder.addClass(e),
            "addClass declares it returns this: chaining must work.");
    }

    @Test
    void rendering_isDeterministic() {
        UmlEntity e = entity(List.of(Field), List.of(Method));

        assertEquals(rendered(e), rendered(e), "Two renderings of the same entity must be identical.");
    }
}
