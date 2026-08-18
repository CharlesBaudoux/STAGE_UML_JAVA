package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.model.UmlAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantUmlAssociationClassTest {

    private static UmlEntity box(String name) {
        return UmlEntity.builder("com.example." + name, name).build();
    }

    private static UmlEntity registration() {
        return UmlEntity.builder("com.example.Inscription", "Inscription")
            .relations(List.of(new UmlAssociationClass("com.example.Etudiant",
                "com.example.Course")))
            .build();
    }

    private static String document() {
        return PlantUmlAssert.documentFor(box("Etudiant"), box("Course"), registration());
    }

    @Test
    void theLine_joinsBothEndpointsToTheClass() {
        String line = PlantUmlAssert.linkLines(document()).get(0);

        assertTrue(line.matches("\\(\\S+, \\S+\\) \\.\\. \\S+"),
            "Expected \u00ab (source, target) .. holder \u00bb, read: " + line);
    }

    @Test
    void theThreeBoxes_areDesignated() {
        String document = document();
        String line = PlantUmlAssert.linkLines(document).get(0);

        for (String alias : PlantUmlAssert.aliasDeclared(document)) {
            assertTrue(line.contains(alias), "Alias " + alias + " is missing from: " + line);
        }
    }

    @Test
    void aSingleLine_isWritten() {
        assertEquals(1, PlantUmlAssert.linkLines(document()).size());
    }

    @Test
    void theSource_mustHaveBeenDeclared() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PlantUmlAssert.documentFor(box("Course"), registration()));

        assertTrue(e.getMessage().contains("Etudiant"), e.getMessage());
    }

    @Test
    void theTarget_mustHaveBeenDeclared() {
        assertThrows(IllegalArgumentException.class,
            () -> PlantUmlAssert.documentFor(box("Etudiant"), registration()));
    }

    @Test
    void theEngineAcceptsTheSyntax() {
        PlantUmlAssert.assertCompile(document());
    }
}
