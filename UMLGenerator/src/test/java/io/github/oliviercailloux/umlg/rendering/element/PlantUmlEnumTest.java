package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantUmlEnumTest {

    private static UmlEntity.Builder enumeration(String name) {
        return UmlEntity.builder("pkg." + name, name)
                        .kind(UmlEntityKind.ENUM)
                        .strategy(UMLVisibility.PUBLIC);
    }

    @Test
    void literal_isWrittenBare() {
        String source = PlantUmlAssert.documentFor(
            enumeration("Currency").literals(List.of("Euro", "Dollar")).build());

        assertTrue(source.contains("  Euro\n"), source);
        assertTrue(source.contains("  Dollar\n"), source);
    }

    @Test
    void literal_neverHasVisibilityTypeOrStatic() {
        String source = PlantUmlAssert.documentFor(
            enumeration("Currency").literals(List.of("Euro")).build());

        assertFalse(source.contains("+ Euro"), source);
        assertFalse(source.contains("{static}"), source);
        assertFalse(source.contains("Currency Euro"), source);
    }

    @Test
    void literalsAlone_haveNoSeparatingLine() {
        String source = PlantUmlAssert.documentFor(
            enumeration("Currency").literals(List.of("Euro", "Dollar")).build());

        assertFalse(source.contains("  --\n"), source);
    }

    @Test
    void literalsAndFields_areSeparatedByALine() {
        String source = PlantUmlAssert.documentFor(
            enumeration("Planet")
                .literals(List.of("Earth"))
                .fields(List.of(new UmlField("mass", "double", "-", false)))
                .build());

        assertEquals(List.of("  Earth", "  --", "  - mass : double"),
            boxBody(source));
    }

    @Test
    void threeCompartments_receiveTwoLines() {
        String source = PlantUmlAssert.documentFor(
            enumeration("Planet")
                .literals(List.of("Earth"))
                .fields(List.of(new UmlField("mass", "double", "-", false)))
                .methods(List.of(new UmlMethod("mass", "double", "+", false, List.of())))
                .build());

        assertEquals(
            List.of("  Earth", "  --", "  - mass : double", "  --", "  + mass() : double"),
            boxBody(source));
    }

    @Test
    void ordinaryClass_keepsItsSingleNet() {
        String source = PlantUmlAssert.documentFor(
            UmlEntity.builder("pkg.Account", "Account")
                     .fields(List.of(new UmlField("balance", "double", "-", false)))
                     .methods(List.of(new UmlMethod("remove", "void", "+", false,
                         List.of("double"))))
                     .build());

        assertEquals(List.of("  - balance : double", "  --", "  + remove(double) : void"),
            boxBody(source));
    }

    @Test
    void renderedEnumeration_isAcceptedByPlantUml() {
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentFor(
            enumeration("Currency")
                .literals(List.of("Euro", "Dollar"))
                .fields(List.of(new UmlField("code", "String", "-", false)))
                .build()));
    }

    private static List<String> boxBody(String source) {
        return source.lines().filter(l -> l.startsWith("  ")).toList();
    }
}
