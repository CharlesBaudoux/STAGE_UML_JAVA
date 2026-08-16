package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

// The cross-cutting invariants of the model: refusal of nulls, defensive copies, immutability of what
// what is exposed, and value equality.
//
// These last three properties follow from the choice of records and ImmutableList; testing them
// explicitly freezes them, so that replacing ImmutableList by an ordinary List does not
// does not go unnoticed.
class UmlModelTest {

    // Shared factory: an entity with nothing, when only the object matters and not its content.
    private static UmlEntity entityMinimum() {
        return new UmlEntity("E", "", "", UMLVisibility.NONE, List.of(), List.of());
    }

    // --- UmlProject ---

    @Test
    void emptyProject_isRefused() {
        // A diagram without a class is meaningless: an explicit error is better than a
        // a blank SVG.
        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> UmlProject.given(Set.of()));

        // The message is checked too: it is what the user will read, and the second argument
        // of assertTrue makes it appear in the report if the test fails.
        assertTrue(e.getMessage().contains("at least one annotated class"), e.getMessage());
    }

    @Test
    void nullProject_isRefused() {
        assertThrows(NullPointerException.class, () -> UmlProject.given(null));
        assertThrows(NullPointerException.class,
            () -> new UmlProject(null, UmlDiagram.defaults(), ImmutableList.of()));
    }

    @Test
    void projectWithoutOverallSettings_isRefused() {
        // The second component is never null: the whole rendering counts on its presence, and
        // UmlDiagram.defaults() is there to say "nothing in particular" without having to omit it.
        assertThrows(NullPointerException.class,
            () -> UmlProject.given(Set.of(entityMinimum()), null));
    }

    @Test
    void projectWithoutDeclaredDiagram_takesTheDefaultSettings() {
        // The short form of given() is the one used by a project setting nothing of the drawing
        // overall, that is, the vast majority of them.
        assertEquals(UmlDiagram.defaults(), UmlProject.given(Set.of(entityMinimum())).diagram());
    }

    // IDENTITY, HELD BY THE PROJECT
    //
    // UmlEntity says of qualifiedName that it is "the identity of the entity". But the ImmutableSet of
    // entities deduplicate over the fourteen components of the record: two entities with the same name
    // qualified names differing only by a colour both fitted there, and the fault did not
    // showed only at rendering time — or not at all in purely programmatic use.

    @Test
    void twoEntitiesWithTheSameQualifiedName_areRefused() {
        UmlEntity blue = UmlEntity.builder("com.bank.Account", "Account").color("#DDEEFF").build();
        UmlEntity red = UmlEntity.builder("com.bank.Account", "Account").color("#FFDDDD").build();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> UmlProject.given(new LinkedHashSet<>(List.of(blue, red))));

        assertTrue(e.getMessage().contains("com.bank.Account"),
            "The message must name the duplicate, the only thing to fix: " + e.getMessage());
    }

    @Test
    void twoEntitiesWithDistinctQualifiedNames_coexist() {
        // The check bears on the qualified name, and not on the displayed name: two classes with the same
        // the same simple name in two different packages are two legitimate boxes.
        UmlEntity here = UmlEntity.builder("com.bank.Account", "Account").build();
        UmlEntity elsewhere = UmlEntity.builder("com.clients.Account", "Account").build();

        assertEquals(2, UmlProject.given(new LinkedHashSet<>(List.of(here, elsewhere)))
            .entities().size());
    }

    @Test
    void entitiesOfAProject_areImmutable() {
        UmlProject project = UmlProject.given(Set.of(entityMinimum()));

        // A Guava immutable collection does not refuse the addition with a boolean: it raises.
        assertThrows(UnsupportedOperationException.class,
            () -> project.entities().add(entityMinimum()));
    }

    @Test
    void project_keepsInsertionOrder() {
        // ImmutableSet preserves the order: that is what makes the diagram reproducible from one
        // scan to the next.
        UmlEntity a = new UmlEntity("A", "", "", UMLVisibility.NONE, List.of(), List.of());
        UmlEntity b = new UmlEntity("B", "", "", UMLVisibility.NONE, List.of(), List.of());
        UmlEntity c = new UmlEntity("C", "", "", UMLVisibility.NONE, List.of(), List.of());

        UmlProject project = UmlProject.given(new LinkedHashSet<>(List.of(b, c, a)));

        assertEquals(List.of("B", "C", "A"),
            project.entities().stream().map(UmlEntity::name).toList());
    }

    @Test
    void project_doesNotNeedlesslyCopyAnImmutableSet() {
        // ImmutableSet.copyOf returns the argument as is if it is already immutable: nothing to copy,
        // it can no longer change.
        ImmutableSet<UmlEntity> entities = ImmutableSet.of(entityMinimum());

        assertEquals(entities, UmlProject.given(entities).entities());
    }

    // Value equality, provided by the record: two projects with the same content are the same
    // project. That is what makes the assertEquals of the other tests possible.
    @Test
    void projectsWithTheSameEntities_areEqual() {
        assertEquals(UmlProject.given(Set.of(entityMinimum())),
            UmlProject.given(Set.of(entityMinimum())));
    }

    // --- UmlEntity ---

    // Each parameter is set to null in turn: a forgotten checkNotNull would show, whereas
    // than a single test case would show.
    @Test
    void entity_refusesEveryNullArgument() {
        assertThrows(NullPointerException.class,
            () -> new UmlEntity(null, "", "", UMLVisibility.NONE, List.of(), List.of()));
        assertThrows(NullPointerException.class,
            () -> new UmlEntity("E", null, "", UMLVisibility.NONE, List.of(), List.of()));
        assertThrows(NullPointerException.class,
            () -> new UmlEntity("E", "", null, UMLVisibility.NONE, List.of(), List.of()));
        assertThrows(NullPointerException.class,
            () -> new UmlEntity("E", "", "", null, List.of(), List.of()));
        assertThrows(NullPointerException.class,
            () -> new UmlEntity("E", "", "", UMLVisibility.NONE, null, List.of()));
        assertThrows(NullPointerException.class,
            () -> new UmlEntity("E", "", "", UMLVisibility.NONE, List.of(), null));
    }

    @Test
    void entity_refusesAnEmptyName() {
        // The last path by which a nameless box entered the model: the
        // direct construction through the API, which ClassHeaderValidator does not see. The rendering
        // then wrote "class \"\" as E", which the engine refuses two layers further on.
        assertThrows(IllegalArgumentException.class,
            () -> new UmlEntity("", "", "", UMLVisibility.NONE, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new UmlEntity("   ", "", "", UMLVisibility.NONE, List.of(), List.of()));
        // The qualified name stays distinct from the displayed name: both are required.
        assertThrows(IllegalArgumentException.class,
            () -> UmlEntity.builder("com.bank.Account", "  ").build());
    }

    @Test
    void entity_copiesTheListsReceivedAtConstruction() {
        // Without a defensive copy, the caller could change the content of the diagram after
        // stroke, unbeknownst to the entity.
        List<UmlField> fields = new ArrayList<>();
        fields.add(new UmlField("a", "int", "+", false));
        List<UmlMethod> methods = new ArrayList<>();
        methods.add(new UmlMethod("m", "void", "+", false, List.of()));

        UmlEntity entity = new UmlEntity("E", "", "", UMLVisibility.PUBLIC, fields, methods);

        // The gesture of the test: the original lists are emptied after construction. Without a
        // defensive, the entity would find itself empty as well.
        fields.clear();
        methods.clear();

        assertEquals(1, entity.fields().size(),
            "The field list must not follow the source.");
        assertEquals(1, entity.methods().size(),
            "The method list must not follow the source.");
    }

    @Test
    void entity_exposesImmutableLists() {
        UmlEntity entity = entityMinimum();

        assertThrows(UnsupportedOperationException.class,
            () -> entity.fields().add(new UmlField("a", "int", "+", false)));
        assertThrows(UnsupportedOperationException.class,
            () -> entity.methods().add(new UmlMethod("m", "void", "+", false, List.of())));
    }

    @Test
    void entitiesDifferingByASingleField_areNotEqual() {
        UmlEntity a = new UmlEntity("E", "", "", UMLVisibility.PUBLIC, List.of(), List.of());
        UmlEntity b = new UmlEntity("E", "", "", UMLVisibility.PRIVATE, List.of(), List.of());

        assertNotEquals(a, b, "The strategy is part of the identity of the entity.");
    }

    // --- UmlMethod ---

    @Test
    void method_copiesItsParameterTypes() {
        // The same requirement as for UmlEntity: the signature must not be able to change an
        // once the method is built.
        List<String> parameters = new ArrayList<>(List.of("int", "String"));

        UmlMethod method = new UmlMethod("compute", "void", "+", false, parameters);
        parameters.clear();

        assertEquals(List.of(UmlParameter.unnamed("int"), UmlParameter.unnamed("String")),
            method.parameters(),
            "The signature must not follow the list passed to the constructor.");
    }

    @Test
    void method_exposesAnImmutableParameterList() {
        UmlMethod method = new UmlMethod("compute", "void", "+", false, List.of("int"));

        assertThrows(UnsupportedOperationException.class,
            () -> method.parameters().add(UmlParameter.unnamed("long")));
    }

    @Test
    void methodsWithSameNameButDifferentSignature_areNotEqual() {
        UmlMethod without = new UmlMethod("send", "void", "+", false, List.of());
        UmlMethod with = new UmlMethod("send", "void", "+", false, List.of("String"));

        assertNotEquals(without, with,
            "Two overloads must stay distinct, otherwise a Set would merge them.");
    }

    // --- UmlField ---

    // These two tests check only the value equality generated by the record, whose
    // the list comparisons elsewhere depend on; those that follow bear on the invariants
    // of the compact constructor.
    @Test
    void identicalFields_areEqual() {
        assertEquals(new UmlField("a", "int", "+", false), new UmlField("a", "int", "+", false));
    }

    @Test
    void staticAndNonStaticField_areNotEqual() {
        assertNotEquals(new UmlField("a", "int", "+", true), new UmlField("a", "int", "+", false));
    }

    // THE THREE COMPONENTS THAT WERE NOT HELD
    //
    // UmlField and UmlMethod were the only two records of the model checking neither the name, nor
    // the type, nor the visibility: "new UmlField(null, null, null, false)" passed without flinching,
    // and the rendering wrote "- null : null" in the box, two layers later. The neighbour
    // immediate UmlParameter nevertheless already refused an empty type.

    @Test
    void fieldWithoutName_isRefused() {
        assertThrows(NullPointerException.class, () -> new UmlField(null, "int", "+", false));
        assertThrows(IllegalArgumentException.class, () -> new UmlField("  ", "int", "+", false));
    }

    @Test
    void fieldWithoutType_isRefused() {
        assertThrows(NullPointerException.class, () -> new UmlField("a", null, "+", false));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new UmlField("a", "", "+", false));
        assertTrue(e.getMessage().contains("a"),
            "The message must name the faulty field: " + e.getMessage());
    }

    @Test
    void fieldOfUnknownVisibility_isRefused() {
        assertThrows(NullPointerException.class, () -> new UmlField("a", "int", null, false));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new UmlField("a", "int", "?", false));
        assertTrue(e.getMessage().contains("?"), e.getMessage());
    }

    @Test
    void fieldWithoutWrittenVisibility_isAccepted() {
        // The empty string is no oversight: it is what the "omit visibility" rule produces
        // of the project, which asks that nothing be written before the member.
        assertEquals("", new UmlField("a", "int", "", false).visibility());
    }

    // --- UmlMethod, same invariants ---

    @Test
    void methodWithoutName_isRefused() {
        assertThrows(NullPointerException.class,
            () -> new UmlMethod(null, "void", "+", false, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new UmlMethod(" ", "void", "+", false, List.of()));
    }

    @Test
    void methodOfUnknownVisibility_isRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new UmlMethod("m", "void", "?", false, List.of()));
    }

    @Test
    void methodWithoutWrittenReturnType_isAccepted() {
        // UML holds the return type to be optional, and the "omit return void" rule uses that:
        // the empty string says "write nothing after the parentheses".
        assertEquals("", new UmlMethod("m", "", "+", false, List.of()).returnType());
    }

    @Test
    void methodWhoseReturnTypeIsOnlySpaces_isRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new UmlMethod("m", " ", "+", false, List.of()));
    }
}
