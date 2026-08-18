package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConstraintExtractorTest {

    private static Optional<UmlField> field(UmlEntity e, String name) {
        return e.fields().stream().filter(f -> f.name().equals(name)).findFirst();
    }

    @UMLClass
    static class Order {}

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    @UMLConstraint(rule = "balance >= overdraftAllowed")
    @UMLConstraint(rule = "holder major")
    static class AccountBank {

        @UMLConstraint(rule = "always positive")
        private double balance;

        private double overdraftAllowed;

        @UMLConstraint(rule = "subset")
        @UMLAssociation(target = Order.class, targetRole = "purchases",
            targetMultiplicity = "0..*")
        private List<Order> orders;
    }

    private static UmlEntity account() {
        return EntityExtractor.extract(AccountBank.class);
    }

    @Test
    void classConstraints_areAllKept() {
        assertEquals(List.of("balance >= overdraftAllowed", "holder major"),
            account().constraints());
    }

    @Test
    void fieldConstraint_followsTheField() {
        assertEquals(List.of("always positive"),
            field(account(), "balance").orElseThrow().constraints());
    }

    @Test
    void neighbouringField_staysWithoutConstraint() {
        assertEquals(List.of(), field(account(), "overdraftAllowed").orElseThrow().constraints());
    }

    @Test
    void constraintOnAFieldPromotedToLink_followsTheLink() {
        assertEquals(List.of("subset"), account().associations().get(0).constraints());
    }

    static class EmptyRule {

        @UMLConstraint(rule = "  ")
        private double balance;
    }

    @Test
    void ruleMadeOfSpaces_isRefusedNamingTheLocation() throws Exception {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ConstraintExtractor.of(EmptyRule.class.getDeclaredField("balance"),
                "EmptyRule.balance"));

        assertTrue(e.getMessage().contains("EmptyRule.balance"), e.getMessage());
        assertTrue(e.getMessage().contains("@UMLConstraint"), e.getMessage());
    }
}
