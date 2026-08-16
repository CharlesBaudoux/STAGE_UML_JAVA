package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import org.junit.jupiter.api.Test;

// The extraction of associations: its interplay with the field compartment, the
// the carrying over of the annotation parameters, and the quality of the error messages.
//
// Target resolution, for its part, has its own test (TargetResolverTest).
class AssociationExtractorTest {

    // Two possible targets; they must carry @UMLClass to be aimed at.
    @UMLClass
    static class Order {}

    @UMLClass
    static class Line {}

    // Extracts the single expected association, failing with a useful message if there are zero
    // or several. Avoids repeating the same check in each test.
    private static UmlAssociation onlyAssociation(Class<?> clazz) {
        List<UmlAssociation> associations = EntityExtractor.extract(clazz).associations();
        assertEquals(1, associations.size(),
            () -> "Expected exactly one association on " + clazz.getSimpleName()
                + ", obtenu : " + associations);
        return associations.get(0);
    }

    // --- Interplay with fields and exemptions ---

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class FieldPromoted {
        @UMLAssociation(target = Order.class)
        private Order order;
        private String reference;
    }

    @Test
    void fieldPromotedToAssociation_leavesTheFieldCompartment() {
        // In UML a property is written in the box or as a link, never both.
        UmlEntity entity = EntityExtractor.extract(FieldPromoted.class);

        assertEquals(List.of("reference"), entity.fields().stream().map(UmlField::name).toList());
        assertEquals(1, entity.associations().size());
    }

    @UMLClass
    static class PrivateFieldWithoutStrategy {
        @UMLAssociation(target = Order.class)
        private Order order;
    }

    @Test
    void associationOnAnInvisibleField_isDrawnAnyway() {
        // The strategy filters the content of the boxes, not the links between them: a
        // a composition between two classes of the model does not vanish because the field carrying it
        // carrying it is private — that is the most common case.
        UmlEntity entity = EntityExtractor.extract(PrivateFieldWithoutStrategy.class);

        assertTrue(entity.fields().isEmpty());
        assertEquals(1, entity.associations().size());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class FieldExcluded {
        @UMLAssociation(target = Order.class)
        @UMLExclude
        private Order order;
    }

    @Test
    void umlExclude_silencesTheAssociationAndTheField() {
        UmlEntity entity = EntityExtractor.extract(FieldExcluded.class);

        assertTrue(entity.associations().isEmpty(), "@UMLExclude must remove the link.");
        assertTrue(entity.fields().isEmpty(),
            "The promoted field must not reappear in the box.");
    }

    @UMLClass
    static class WithoutAssociation {
        private Order order;
    }

    @Test
    void withoutAnnotation_noAssociationIsInferred() {
        // Automatic inference of links is not the business of this annotation: a
        // attribute nu remains un attribute.
        assertTrue(EntityExtractor.extract(WithoutAssociation.class).associations().isEmpty());
    }

    // --- Parameters ---

    @UMLClass
    static class SpecificationExample {
        @UMLAssociation(
            target = Line.class,
            type = UMLRelationType.COMPOSITION,
            sourceMultiplicity = "1",
            targetMultiplicity = "0..*",
            targetRole = "purchases",
            label = "passed",
            sourceRole = "client",
            direction = UMLDirection.DOWN)
        private List<Line> lines;
    }

    @Test
    void everyParameter_isCarriedOverToTheModel() {
        UmlAssociation association = onlyAssociation(SpecificationExample.class);

        assertEquals("lines", association.fieldName());
        assertEquals(Line.class.getName(), association.targetQualifiedName());
        assertEquals(UMLRelationType.COMPOSITION, association.type());
        assertEquals("1", association.sourceMultiplicity());
        assertEquals("0..*", association.targetMultiplicity());
        assertEquals("client", association.sourceRole());
        assertEquals("purchases", association.targetRole());
        assertEquals("passed", association.label());
        assertEquals(UMLDirection.DOWN, association.direction());
    }

    @UMLClass
    static class AnnotationBare {
        @UMLAssociation(target = Order.class)
        private Order order;
    }

    // The counterpart of the previous test: apart from target, mandatory, everything must mean "nothing" —
    // the empty string, NONE — and never null, which the rendering would have to test everywhere.
    @Test
    void byDefault_everyOptionalValueIsNeutral() {
        UmlAssociation association = onlyAssociation(AnnotationBare.class);

        assertEquals(UMLRelationType.ASSOCIATION, association.type());
        assertEquals(UMLDirection.NONE, association.direction());
        assertEquals("", association.sourceMultiplicity());
        assertEquals("", association.targetMultiplicity());
        assertEquals("", association.sourceRole());
        assertEquals("", association.targetRole());
        assertEquals("", association.label());
        assertTrue(association.constraints().isEmpty());
    }

    // --- The "where" added to the "what" ---

    // Without @UMLClass, deliberately: the test calls the extractor directly, without going through
    // EntityExtractor, which would require the annotation.
    static class FieldInvalidMultiplicity {
        @UMLAssociation(target = Order.class, targetMultiplicity = "many")
        private Order order;
    }

    @Test
    void invalidMultiplicity_isRefusedNamingItsLocation() {
        // On a project of forty classes, "\"many\" is not a multiplicity" is a
        // a message leading nowhere: extraction adds the "where" to it.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new AssociationExtractor().extract(FieldInvalidMultiplicity.class));

        assertTrue(e.getMessage().contains("order")
                && e.getMessage().contains("targetMultiplicity"),
            () -> "Message too poor to locate the fault: " + e.getMessage());
    }

    @Test
    void associationList_isImmutable() {
        List<UmlAssociation> associations =
            EntityExtractor.extract(AnnotationBare.class).associations();

        assertThrows(UnsupportedOperationException.class, () -> associations.clear());
    }
}
