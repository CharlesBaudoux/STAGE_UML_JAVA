package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Checks the guards of the UmlInheritance record, and its place among the relations.
class UmlInheritanceTest {

    @Test
    void emptyOrNullTarget_isRefused() {
        assertThrows(NullPointerException.class,
            () -> new UmlInheritance(null, UmlInheritanceType.GENERALIZATION));
        assertThrows(NullPointerException.class, () -> new UmlInheritance("Target", null));
        assertThrows(IllegalArgumentException.class,
            () -> new UmlInheritance("  ", UmlInheritanceType.GENERALIZATION));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pkg.Vehicle", "Vehicle", "a.b.c.D$Internal"})
    void validTarget_isKept(String target) {
        assertEquals(target, UmlInheritance.generalization(target).targetQualifiedName());
    }

    // An inheritance link never carries text: the UmlRelation contract imposes the method,
    // the empty string says absence. RelationRenderer then writes nothing after the stroke.
    @Test
    void inheritance_neverHasALabel() {
        assertEquals("", UmlInheritance.generalization("Target").label());
        assertEquals("", UmlInheritance.realization("Target").label());
    }

    @Test
    void factories_setTheRightType() {
        assertEquals(UmlInheritanceType.GENERALIZATION,
            UmlInheritance.generalization("Target").type());
        assertEquals(UmlInheritanceType.REALIZATION, UmlInheritance.realization("Target").type());
    }

    // A record: two inheritance links with the same target and nature are the same object.
    @Test
    void twoIdenticalInheritances_areEqual() {
        assertEquals(UmlInheritance.generalization("Target"),
            UmlInheritance.generalization("Target"));
    }

    // The relation must let itself be sorted by UmlEntity.relations(Class) like the other two:
    // that is what lets specialized accessors coexist in a single list.
    @Test
    void inheritances_sortWithTheOtherRelations() {
        UmlInheritance inheritance = UmlInheritance.generalization("pkg.Parent");
        UmlDependency dependency = UmlDependency.toTarget("pkg.Service");

        UmlEntity entity = UmlEntity.builder("pkg.Child", "Child")
            .relations(ImmutableList.of(inheritance, dependency))
            .build();

        assertEquals(ImmutableList.of(inheritance), entity.inheritances());
        assertEquals(ImmutableList.of(dependency), entity.dependencies());
        assertEquals(ImmutableList.of(), entity.associations());
    }
}
