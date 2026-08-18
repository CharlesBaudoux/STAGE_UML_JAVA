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

class TargetResolverTest {

    @UMLClass
    static class Order {}

    static class OutsideDiagram {}

    private static UmlAssociation onlyAssociation(Class<?> clazz) {
        List<UmlAssociation> associations = EntityExtractor.extract(clazz).associations();
        assertEquals(1, associations.size(),
            () -> "Expected exactly one association on " + clazz.getSimpleName()
                + ", obtenu : " + associations);
        return associations.get(0);
    }

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
        @UMLAssociation(target = Order.class)
        private List<Object> blackBox;
    }

    @Test
    void declaredTarget_isNeverComparedToTheFieldType() {
        assertEquals(Order.class.getName(),
            onlyAssociation(FieldTargetIndependentOfTheType.class).targetQualifiedName());
    }

    static class FieldOutsideDiagram {
        @UMLAssociation(target = OutsideDiagram.class)
        private OutsideDiagram dependency;
    }

    @Test
    void targetWithoutUmlClass_isRefused() {
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
