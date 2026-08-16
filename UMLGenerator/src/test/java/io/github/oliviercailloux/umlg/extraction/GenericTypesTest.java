package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

// What reflection knows of type parameters, and what the diagram makes of it.
//
// WHAT THIS FILE PROTECTS
//
// The library long displayed "List" where the code writes "List<Order>", on the pattern
// that compilation erases type parameters. That is true of what lives at run time, and
// wrong about declared signatures: the compiler keeps them in the "Signature" attribute of the
// the .class file, and reflection returns them through getGenericType().
//
// The difference is not cosmetic. "Map<String, Short>" says what the table maps;
// "Map" says nothing. And that is what makes it possible to declare the target of an association only
// the type already names.
class GenericTypesTest {

    @UMLClass
    static class Order {}

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Library {
        private List<String> titles;
        private Map<String, Integer> accounts;
        private Set<? extends Order> inProgress;
        private List<String>[] pages;
        private String simple;

        public Map<String, List<Order>> byClient() {
            return Map.of();
        }

        public void add(List<Order> batch) {}
    }

    private static UmlField field(UmlEntity entity, String name) {
        return entity.fields().stream().filter(f -> f.name().equals(name)).findFirst()
            .orElseThrow();
    }

    private static UmlEntity extracted() {
        return EntityExtractor.extract(Library.class);
    }

    // --- Types as they are written ---

    @Test
    void aTypeParameter_isDisplayed() {
        assertEquals("List<String>", field(extracted(), "titles").type());
    }

    @Test
    void twoParameters_areBothDisplayed() {
        assertEquals("Map<String, Integer>", field(extracted(), "accounts").type());
    }

    @Test
    void aWildcard_keepsItsBound() {
        assertEquals("Set<? extends Order>", field(extracted(), "inProgress").type());
    }

    @Test
    void anArrayOfGenerics_keepsItsBrackets() {
        assertEquals("List<String>[]", field(extracted(), "pages").type());
    }

    @Test
    void anOrdinaryType_isUnchanged() {
        assertEquals("String", field(extracted(), "simple").type());
    }

    @Test
    void theParameters_alsoHoldForMethods() {
        var byClient = extracted().methods().stream()
            .filter(m -> m.name().equals("byClient")).findFirst().orElseThrow();
        var add = extracted().methods().stream()
            .filter(m -> m.name().equals("add")).findFirst().orElseThrow();

        assertEquals("Map<String, List<Order>>", byClient.returnType());
        assertEquals("List<Order>", add.parameters().get(0).type());
    }

    @Test
    void theRenamingRules_enterTheAngleBrackets() {
        // "ImmutableList" is renamed "List" wherever it appears, including as a parameter.
        Rules rules = Rules.read("""
            { "types": { "ImmutableList": "List" } }
            """,
            "the rules of the test");

        assertEquals("List<String>",
            fieldType(rules, "titles"));
    }

    private static String fieldType(Rules rules, String name) {
        return EntityExtractor.extract(Library.class, rules).fields().stream()
            .filter(f -> f.name().equals(name)).findFirst().orElseThrow().type();
    }

    // --- The element type when a multiplicity is written ---

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class WithMultiplicities {
        @UMLMultiplicity("0..*")
        private Set<String> alias;

        @UMLMultiplicity("0..1")
        private Optional<Integer> ucp;

        @UMLMultiplicity("0..*")
        private Map<String, Integer> table;

        @UMLMultiplicity("0..*")
        public List<Order> orders() {
            return List.of();
        }
    }

    @Test
    void withAMultiplicity_theElementTypeIsWritten() {
        // "alias : Set [0..*]" would read "zero or more sets", which is wrong: there
        // there is a set, holding zero or more strings. UML wants the element type here.
        UmlEntity entity = EntityExtractor.extract(WithMultiplicities.class);

        assertEquals("String", field(entity, "alias").type());
        assertEquals("0..*", field(entity, "alias").multiplicity());
    }

    @Test
    void optional_isAWrapperLikeAnyOther() {
        // And its element becomes primitive again: Java forbids "Optional<int>", the wrapper
        // Integer is a constraint of the language, not a choice of the author. "int [0..1]" is what
        // what the diagram must say — "Integer [0..1]" would keep the trace of a constraint
        // which the reader need not know.
        assertEquals("int", field(EntityExtractor.extract(WithMultiplicities.class), "ucp")
            .type());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class WithWrappers {
        @UMLMultiplicity("1..*")
        private String[] names;

        @UMLMultiplicity("0..*")
        private List<? extends CharSequence> texts;

        @UMLMultiplicity("0..*")
        private List<? super Order> receptacles;

        @UMLMultiplicity("0..*")
        private List<String[]> batches;

        @UMLMultiplicity("1..*")
        private Integer[] counters;
    }

    @Test
    void anArray_beforeAMultiplicity_losesItsBrackets() {
        // "names : String[] [1..*]" reads "one or more arrays of strings", whereas the
        // the code declares only one. An array is the oldest way Java has of writing
        // "several": before multiplicity brackets, it is the wrapper, like a Set.
        assertEquals("String", field(EntityExtractor.extract(WithWrappers.class), "names").type());
    }

    @Test
    void aWildcard_beforeAMultiplicity_comesDownToItsBound() {
        // "? extends CharSequence [0..*]" says "zero or more things extending
        // CharSequence"; the bound is all the wildcard brought.
        UmlEntity entity = EntityExtractor.extract(WithWrappers.class);

        assertEquals("CharSequence", field(entity, "texts").type());
        // With no upper bound, the wildcard has nothing more to say and is written as is — "Object"
        // would be a claim the code does not make.
        assertEquals("?", field(entity, "receptacles").type());
    }

    @Test
    void asingleWrapper_isRemoved() {
        // "zero or more arrays of strings" is exactly what the code declares here:
        // the array is inside, and it is the list that carries the multiplicity.
        UmlEntity entity = EntityExtractor.extract(WithWrappers.class);

        assertEquals("String[]", field(entity, "batches").type());
        // And unwrapping down to the primitive stays reserved for containers: "Integer[]" is an
        // the author's choice, which "int[]" already offered.
        assertEquals("Integer", field(entity, "counters").type());
    }

    @Test
    void aMapIsNotUnwrapped() {
        // Two parameters: nothing would say which one is the property. The type stays written in
        // as a whole, which remains accurate.
        assertEquals("Map<String, Integer>",
            field(EntityExtractor.extract(WithMultiplicities.class), "table").type());
    }

    @Test
    void theRule_alsoHoldsForAMethodReturn() {
        var orders = EntityExtractor.extract(WithMultiplicities.class).methods().stream()
            .filter(m -> m.name().equals("orders")).findFirst().orElseThrow();

        assertEquals("Order", orders.returnType());
        assertEquals("0..*", orders.multiplicity());
    }

    @Test
    void withoutMultiplicity_theContainerStaysWritten() {
        // Without brackets, "List<Order>" is the type of the property, and writing it in full
        // is the only right thing.
        assertEquals("List<String>", field(extracted(), "titles").type());
    }

    // --- The target of an association, inferred from the type ---

    @UMLClass
    static class InferredClient {
        @UMLAssociation(targetMultiplicity = "0..*", notation = UMLNotation.ATTRIBUTE)
        private List<Order> orders;

        @UMLAssociation(notation = UMLNotation.ATTRIBUTE)
        private Order current;
    }

    @Test
    void theTargetOfACollection_isReadInItsParameter() {
        UmlEntity entity = EntityExtractor.extract(InferredClient.class);

        assertEquals("Order", field(entity, "orders").type());
        assertEquals("0..*", field(entity, "orders").multiplicity());
    }

    @Test
    void theTargetOfASimpleField_isItsTypeItself() {
        assertEquals("Order",
            field(EntityExtractor.extract(InferredClient.class), "current").type());
    }

    @UMLClass
    static class InferredLinkAsStroke {
        @UMLAssociation(targetMultiplicity = "1..*")
        private List<Order> orders;
    }

    @Test
    void theInference_alsoHoldsForALinkDrawnAsAStroke() {
        ImmutableList<UmlAssociation> links =
            EntityExtractor.extract(InferredLinkAsStroke.class).associations();

        assertEquals(1, links.size());
        assertEquals(Order.class.getName(), links.get(0).targetQualifiedName());
    }

    // The faulty fixtures carry no @UMLClass: the scan of target/test-classes
    // would meet them and would fail.
    static class CollectionRaw {
        @SuppressWarnings("rawtypes")
        @UMLAssociation(notation = UMLNotation.ATTRIBUTE)
        private List orders;
    }

    @Test
    void aCollectionWithoutParameter_requiresTheTargetToBeWritten() {
        // This is exactly the gap target() is there to fill: a raw list says
        // nothing of what it holds.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(CollectionRaw.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("does not say what it contains"), e.getMessage());
    }
}
