package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class DerivedAssociationTest {

    @UMLClass
    static class Order {}

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Client {

        @UMLDerived
        @UMLAssociation(target = Order.class, targetRole = "inProgress",
            targetMultiplicity = "0..*")
        private List<Order> ordersInProgress;

        @UMLAssociation(target = Order.class, targetRole = "archives",
            targetMultiplicity = "0..*")
        private List<Order> ordersArchived;
    }

    private static UmlEntity client() {
        return EntityExtractor.extract(Client.class);
    }

    @Test
    void annotatedLink_isSaidDerived() {
        assertTrue(client().associations().stream()
            .filter(a -> a.targetRole().equals("inProgress"))
            .findFirst().orElseThrow().derived());
    }

    @Test
    void neighbouringLink_staysOrdinary() {
        assertFalse(client().associations().stream()
            .filter(a -> a.targetRole().equals("archives"))
            .findFirst().orElseThrow().derived());
    }

    @Test
    void theFieldDoesNotComeBackIntoTheBox() {
        UmlEntity entity = client();

        assertTrue(entity.fields().stream()
            .noneMatch(f -> f.name().equals("ordersInProgress")),
            "The field carrying the link must not appear in the box: " + entity.fields());
    }
}
