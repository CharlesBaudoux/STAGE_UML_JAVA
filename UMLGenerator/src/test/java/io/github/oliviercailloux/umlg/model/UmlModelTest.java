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

class UmlModelTest {

    private static UmlEntity entityMinimum() {
        return new UmlEntity("E", "", "", UMLVisibility.NONE, List.of(), List.of());
    }

    @Test
    void emptyProject_isRefused() {
        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> UmlProject.given(Set.of()));

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
        assertThrows(NullPointerException.class,
            () -> UmlProject.given(Set.of(entityMinimum()), null));
    }

    @Test
    void projectWithoutDeclaredDiagram_takesTheDefaultSettings() {
        assertEquals(UmlDiagram.defaults(), UmlProject.given(Set.of(entityMinimum())).diagram());
    }

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
        UmlEntity here = UmlEntity.builder("com.bank.Account", "Account").build();
        UmlEntity elsewhere = UmlEntity.builder("com.clients.Account", "Account").build();

        assertEquals(2, UmlProject.given(new LinkedHashSet<>(List.of(here, elsewhere)))
            .entities().size());
    }

    @Test
    void entitiesOfAProject_areImmutable() {
        UmlProject project = UmlProject.given(Set.of(entityMinimum()));

        assertThrows(UnsupportedOperationException.class,
            () -> project.entities().add(entityMinimum()));
    }

    @Test
    void project_keepsInsertionOrder() {
        UmlEntity a = new UmlEntity("A", "", "", UMLVisibility.NONE, List.of(), List.of());
        UmlEntity b = new UmlEntity("B", "", "", UMLVisibility.NONE, List.of(), List.of());
        UmlEntity c = new UmlEntity("C", "", "", UMLVisibility.NONE, List.of(), List.of());

        UmlProject project = UmlProject.given(new LinkedHashSet<>(List.of(b, c, a)));

        assertEquals(List.of("B", "C", "A"),
            project.entities().stream().map(UmlEntity::name).toList());
    }

    @Test
    void project_doesNotNeedlesslyCopyAnImmutableSet() {
        ImmutableSet<UmlEntity> entities = ImmutableSet.of(entityMinimum());

        assertEquals(entities, UmlProject.given(entities).entities());
    }

    @Test
    void projectsWithTheSameEntities_areEqual() {
        assertEquals(UmlProject.given(Set.of(entityMinimum())),
            UmlProject.given(Set.of(entityMinimum())));
    }

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
        assertThrows(IllegalArgumentException.class,
            () -> new UmlEntity("", "", "", UMLVisibility.NONE, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new UmlEntity("   ", "", "", UMLVisibility.NONE, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> UmlEntity.builder("com.bank.Account", "  ").build());
    }

    @Test
    void entity_copiesTheListsReceivedAtConstruction() {
        List<UmlField> fields = new ArrayList<>();
        fields.add(new UmlField("a", "int", "+", false));
        List<UmlMethod> methods = new ArrayList<>();
        methods.add(new UmlMethod("m", "void", "+", false, List.of()));

        UmlEntity entity = new UmlEntity("E", "", "", UMLVisibility.PUBLIC, fields, methods);

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

    @Test
    void method_copiesItsParameterTypes() {
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

    @Test
    void identicalFields_areEqual() {
        assertEquals(new UmlField("a", "int", "+", false), new UmlField("a", "int", "+", false));
    }

    @Test
    void staticAndNonStaticField_areNotEqual() {
        assertNotEquals(new UmlField("a", "int", "+", true), new UmlField("a", "int", "+", false));
    }

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
        assertEquals("", new UmlField("a", "int", "", false).visibility());
    }

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
        assertEquals("", new UmlMethod("m", "", "+", false, List.of()).returnType());
    }

    @Test
    void methodWhoseReturnTypeIsOnlySpaces_isRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new UmlMethod("m", " ", "+", false, List.of()));
    }
}
