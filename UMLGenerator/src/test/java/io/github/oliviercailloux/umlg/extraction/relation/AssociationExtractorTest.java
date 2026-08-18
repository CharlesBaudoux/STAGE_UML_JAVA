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

class AssociationExtractorTest {

    @UMLClass
    static class Order {}

    @UMLClass
    static class Line {}

    private static UmlAssociation onlyAssociation(Class<?> clazz) {
        List<UmlAssociation> associations = EntityExtractor.extract(clazz).associations();
        assertEquals(1, associations.size(),
            () -> "Expected exactly one association on " + clazz.getSimpleName()
                + ", obtenu : " + associations);
        return associations.get(0);
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class FieldPromoted {
        @UMLAssociation(target = Order.class)
        private Order order;
        private String reference;
    }

    @Test
    void fieldPromotedToAssociation_leavesTheFieldCompartment() {
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
        assertTrue(EntityExtractor.extract(WithoutAssociation.class).associations().isEmpty());
    }

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

    static class FieldInvalidMultiplicity {
        @UMLAssociation(target = Order.class, targetMultiplicity = "many")
        private Order order;
    }

    @Test
    void invalidMultiplicity_isRefusedNamingItsLocation() {
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
