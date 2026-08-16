package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.model.UmlAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// La class d'association : « (Etudiant, Course) .. Inscription ».
//
// The only relation with three boxes, and the only one whose line does not start with the class that
// declares it.
class PlantUmlAssociationClassTest {

    private static UmlEntity box(String name) {
        return UmlEntity.builder("com.example." + name, name).build();
    }

    // Registration qualifies the link between Student and Course.
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
        // The aliases are the registry's, not the names: so it is checked that there really are three
        // and distinct, and that the line uses them all.
        String document = document();
        String line = PlantUmlAssert.linkLines(document).get(0);

        for (String alias : PlantUmlAssert.aliasDeclared(document)) {
            assertTrue(line.contains(alias), "Alias " + alias + " is missing from: " + line);
        }
    }

    @Test
    void aSingleLine_isWritten() {
        // The stroke between the two endpoints is drawn by this very line: writing another
        // a second would duplicate the link.
        assertEquals(1, PlantUmlAssert.linkLines(document()).size());
    }

    @Test
    void theSource_mustHaveBeenDeclared() {
        // The second endpoint goes through the same registry as the first: a missing class
        // of the diagram is reported, and not silently recreated by PlantUML.
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
