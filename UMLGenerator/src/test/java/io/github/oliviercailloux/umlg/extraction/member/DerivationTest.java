package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// What @UMLDerived brings into the model.
//
// The annotation says nothing but "this element is computed". The whole question is therefore
// that it reaches the model intact, and that it does not spill over the neighbouring members.
class DerivationTest {

    private static Optional<UmlField> field(UmlEntity e, String name) {
        return e.fields().stream().filter(f -> f.name().equals(name)).findFirst();
    }

    private static Optional<UmlMethod> method(UmlEntity e, String name) {
        return e.methods().stream().filter(m -> m.name().equals(name)).findFirst();
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Person {

        private LocalDate dateBirth;

        @UMLDerived
        private int age;

        @UMLDerived
        public String summarize() {
            return "";
        }

        public void move() {}
    }

    @Test
    void annotatedField_isSaidDerived() {
        UmlEntity entity = EntityExtractor.extract(Person.class);

        assertTrue(field(entity, "age").orElseThrow().derived());
    }

    @Test
    void neighbouringField_staysStored() {
        // Derivation is set member by member: nothing must rub off on the neighbouring
        // side, even one declared just before.
        UmlEntity entity = EntityExtractor.extract(Person.class);

        assertFalse(field(entity, "dateBirth").orElseThrow().derived());
    }

    @Test
    void annotatedMethod_isSaidDerived() {
        UmlEntity entity = EntityExtractor.extract(Person.class);

        assertTrue(method(entity, "summarize").orElseThrow().derived());
        assertFalse(method(entity, "move").orElseThrow().derived());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Cart {

        @UMLDerived
        @UMLInclude
        private double total;
    }

    @Test
    void derivationAndInclusion_combine() {
        // The two annotations answer two distinct questions — "do we show it?" and
        // "is it computed?" —, and must be able to coexist on the same field.
        UmlEntity entity = EntityExtractor.extract(Cart.class);

        assertTrue(field(entity, "total").orElseThrow().derived());
    }
}
