package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void twoIdenticalInheritances_areEqual() {
        assertEquals(UmlInheritance.generalization("Target"),
            UmlInheritance.generalization("Target"));
    }

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
