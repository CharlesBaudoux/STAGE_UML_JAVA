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

// Two fields facing each other are but one association.
//
// The model keeps two of them, because it says what each class declares; the drawing wants
// than one, with two named endpoints, because that is the UML definition of a binary association.
// These tests fix where the merge stops: it must never silently lose what the
// second link says on top.
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
        // The role of an endpoint is the name under which the other end designates it: "B.a" names
        // hence endpoint A of the stroke, and that is what the merge must produce.
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
        // The diamond of a composition sits at the source of the link declaring it. Carrying it over
        // on the opposite endpoint of the kept stroke would put it on the wrong side: better
        // two strokes qu'un stroke faux.
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        assertEquals(1, relations("A", a, b).size());
        assertEquals(1, relations("B", a, b).size());
    }

    @Test
    void aConstraintOnTheOtherSide_preventsTheMerge() {
        // A source endpoint can carry only a role, a multiplicity and a qualifier:
        // the constraint of the second link would have nowhere to go, and would vanish.
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .constraints(List.of("not empty")).build());

        assertEquals(1, relations("A", a, b).size());
        assertEquals(1, relations("B", a, b).size());
    }

    @Test
    void anAlreadyWrittenSourceSide_isRespected() {
        // The user has already said what the source endpoint carries; what they wrote wins
        // on what the class opposite would say about it.
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B")
            .sourceRole("proprietaire").sourceMultiplicity("1").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("other").build());

        assertEquals(1, relations("A", a, b).size());
        assertEquals(1, relations("B", a, b).size());
    }

    @Test
    void twoFieldsTowardsTheSameTarget_stayTwoStrokes() {
        // A single class opposite, but two distinct properties: they really are two
        // associations, and the second has no reciprocal of its own.
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

    // --- The document really generated, and not the computation alone ---

    @Test
    void theGeneratedDocument_containsASingleStroke() {
        // The missing test, whose absence let a silent drift through.
        //
        // The previous tests query ReciprocalAssociations; this one goes through the
        // document, hence through the same sequence of passes as PlantUmlWriter. As long as the helper
        // of the tests copied this sequence rather than calling it, it wrote the old document
        // — two strokes — with no test noticing.
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
        // The counterpart of the previous one: what the merge refuses must show on the drawing too.
        UmlEntity a = entity("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entity("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        assertEquals(2, PlantUmlAssert.linkLines(PlantUmlAssert.documentFor(a, b)).size());
    }

    // --- What the no-merge warning says ---
    //
    // It stated only one of the two causes, and its remedy — "decorate a single side only" —
    // was inapplicable to the other: when the kept link has already written its own source side,
    // removing the decorations of the partner changes nothing to the drawing.

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
        // The case the message silenced: nothing is decorated, and yet the merge did not happen.
        // The advice "decorate a single side only" had no purchase there.
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
        // Fixing only one would leave both strokes, with no one knowing why.
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
        // A class referencing itself has no distinct "opposite": the stroke
        // would come back on itself, and there is nothing to absorb.
        UmlEntity a = entity("A", UmlAssociation.builder("parent", "dm.A").build());

        assertEquals(1, relations("A", a).size());
    }
}
