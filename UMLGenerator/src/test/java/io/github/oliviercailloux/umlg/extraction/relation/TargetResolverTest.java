package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import java.util.List;
import org.junit.jupiter.api.Test;

// "Is the class at the other end of the stroke allowed to be there?"
//
// target() being mandatory (with no default, javac already refuses an annotation missing it),
// there is nothing left to infer: these tests check validation only — the declared target
// is accepted as is, or refused with a message naming the fault.
class TargetResolverTest {

    @UMLClass
    static class Order {}

    // Without @UMLClass: serves as a forbidden target.
    static class OutsideDiagram {}

    private static UmlAssociation onlyAssociation(Class<?> clazz) {
        List<UmlAssociation> associations = EntityExtractor.extract(clazz).associations();
        assertEquals(1, associations.size(),
            () -> "Expected exactly one association on " + clazz.getSimpleName()
                + ", obtenu : " + associations);
        return associations.get(0);
    }

    // --- Valid target ---

    @UMLClass
    static class FieldSimple {
        @UMLAssociation(target = Order.class)
        private Order order;
    }

    @Test
    void declaredTarget_isTakenAsIs() {
        assertEquals(Order.class.getName(),
            onlyAssociation(FieldSimple.class).targetQualifiedName());
    }

    @UMLClass
    static class FieldTargetIndependentOfTheType {
        // The declared target prevails, regardless of the field type: nothing is inferred.
        @UMLAssociation(target = Order.class)
        private List<Object> blackBox;
    }

    @Test
    void declaredTarget_isNeverComparedToTheFieldType() {
        assertEquals(Order.class.getName(),
            onlyAssociation(FieldTargetIndependentOfTheType.class).targetQualifiedName());
    }

    // --- Refused target: the error names the faulty field ---
    //
    // These fixtures deliberately carry no @UMLClass: the sweep of target/test-classes
    // by the integration tests would extract them, and would fail.

    static class FieldOutsideDiagram {
        @UMLAssociation(target = OutsideDiagram.class)
        private OutsideDiagram dependency;
    }

    @Test
    void targetWithoutUmlClass_isRefused() {
        // Without this refusal, PlantUML would invent an empty box carrying that name: a diagram
        // wrong, indistinguishable from a class really without members.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new AssociationExtractor().extract(FieldOutsideDiagram.class));

        assertTrue(e.getMessage().contains("@UMLClass"),
            () -> "The message must explain what is missing: " + e.getMessage());
        assertTrue(e.getMessage().contains("dependency"),
            () -> "The message must name the faulty field: " + e.getMessage());
    }

    static class PrimitiveField {
        @UMLAssociation(target = int.class)
        private int counter;
    }

    @Test
    void primitiveType_isRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new AssociationExtractor().extract(PrimitiveField.class));
    }
}
