package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReciprocalAssociationsTest {

    private static UmlEntity entity(String name, UmlRelation... relations) {
        return UmlEntity.builder("dm." + name, name).relations(List.of(relations)).build();
    }

    private static ImmutableList<UmlRelation> relations(String name, UmlEntity... entities) {
        ImmutableMap<String, ImmutableList<UmlRelation>> merged =
            ReciprocalAssociations.of(List.of(entities));
        return merged.get("dm." + name);
    }

    @Test
    void twoFacingFields_giveASingleStrokeOnly() {
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B")
            .targetRole("bs").targetMultiplicity("0..*").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("a").targetMultiplicity("1").build());

        assertEquals(1, relations("A", a, b).size());
        assertTrue(relations("B", a, b).isEmpty(),
            "The second link is absorbed by the first, not drawn alongside.");
    }

    @Test
    void theMergedStroke_carriesBothRoles() {
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B")
            .targetRole("bs").targetMultiplicity("0..*").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("a").targetMultiplicity("1").build());

        UmlAssociation stroke = (UmlAssociation) relations("A", a, b).get(0);

        assertEquals("a", stroke.sourceRole());
        assertEquals("1", stroke.sourceMultiplicity());
        assertEquals("bs", stroke.targetRole());
        assertEquals("0..*", stroke.targetMultiplicity());
    }

    @Test
    void aDiamondOnTheOtherSide_preventsTheMerge() {
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        assertEquals(1, relations("A", a, b).size());
        assertEquals(1, relations("B", a, b).size());
    }

    @Test
    void aConstraintOnTheOtherSide_preventsTheMerge() {
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .constraints(List.of("not empty")).build());

        assertEquals(1, relations("A", a, b).size());
        assertEquals(1, relations("B", a, b).size());
    }

    @Test
    void anAlreadyWrittenSourceSide_isRespected() {
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B")
            .sourceRole("proprietaire").sourceMultiplicity("1").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("other").build());

        assertEquals(1, relations("A", a, b).size());
        assertEquals(1, relations("B", a, b).size());
    }

    @Test
    void twoFieldsTowardsTheSameTarget_stayTwoStrokes() {
        UmlEntity a = entity("A",
            UmlAssociation.builder("current", "dm.B").build(),
            UmlAssociation.builder("archived", "dm.B").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A").build());

        assertEquals(2, relations("A", a, b).size());
        assertTrue(relations("B", a, b).isEmpty());
    }

    @Test
    void aTargetOutsideTheDiagram_changesNothing() {
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.Missing").build());

        assertEquals(1, relations("A", a).size());
    }

    @Test
    void theGeneratedDocument_containsASingleStroke() {
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B")
            .targetRole("bs").targetMultiplicity("0..*").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("a").targetMultiplicity("1").build());

        List<String> strokes = PlantUmlAssert.linkLines(PlantUmlAssert.documentFor(a, b));

        assertEquals(1, strokes.size(), "One only stroke expected, obtenu : " + strokes);
        assertTrue(strokes.get(0).contains("\"a 1\""), strokes.get(0));
        assertTrue(strokes.get(0).contains("\"bs 0..*\""), strokes.get(0));
    }

    @Test
    void theGeneratedDocument_keepsBothStrokesWhenMergingWouldLose() {
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        assertEquals(2, PlantUmlAssert.linkLines(PlantUmlAssert.documentFor(a, b)).size());
    }

    private static String warningFor(UmlEntity... entities) {
        PrintStream before = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            ReciprocalAssociations.of(List.of(entities));
        } finally {
            System.setErr(before);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void decoratedPartner_isTheAnnouncedCause() {
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        String says = warningFor(a, b);

        assertTrue(says.contains("dm.B.a carries more than a role"), says);
        assertTrue(says.contains("Keep the decorations on one side only"), says);
        assertFalse(says.contains("source end"),
            "That cause does not apply here: " + says);
    }

    @Test
    void sourceSideAlreadyWritten_isTheOtherCauseWithItsOwnRemedy() {
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B")
            .sourceRole("proprietaire").sourceMultiplicity("1").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("other").build());

        String says = warningFor(a, b);

        assertTrue(says.contains("dm.A.bs already names its own source end"), says);
        assertTrue(says.contains("Drop that source end"), says);
        assertFalse(says.contains("Keep the decorations on one side only"),
            "An inapplicable remedy must no longer be offered: " + says);
    }

    @Test
    void bothCauses_areStatedTogether() {
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B")
            .sourceRole("proprietaire").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        String says = warningFor(a, b);

        assertTrue(says.contains("carries more than a role"), says);
        assertTrue(says.contains("already names its own source end"), says);
    }

    @Test
    void aSelfAssociation_isNotMerged() {
        UmlEntity a = entity("A", UmlAssociation.builder("parent", "dm.A").build());

        assertEquals(1, relations("A", a).size());
    }
}
