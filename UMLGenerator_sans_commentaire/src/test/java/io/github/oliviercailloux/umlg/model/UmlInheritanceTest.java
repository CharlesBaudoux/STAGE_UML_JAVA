package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UmlInheritanceTest {

    @Test
    void cibleVideOuNulle_estRefusee() {
        assertThrows(NullPointerException.class,
            () -> new UmlInheritance(null, UmlInheritanceType.GENERALIZATION));
        assertThrows(NullPointerException.class, () -> new UmlInheritance("Cible", null));
        assertThrows(IllegalArgumentException.class,
            () -> new UmlInheritance("  ", UmlInheritanceType.GENERALIZATION));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pkg.Vehicule", "Vehicule", "a.b.c.D$Interne"})
    void cibleValide_estConservee(String cible) {
        assertEquals(cible, UmlInheritance.generalization(cible).targetQualifiedName());
    }

    @Test
    void heritage_nAJamaisDeLabel() {
        assertEquals("", UmlInheritance.generalization("Cible").label());
        assertEquals("", UmlInheritance.realization("Cible").label());
    }

    @Test
    void fabriques_posentLeBonType() {
        assertEquals(UmlInheritanceType.GENERALIZATION,
            UmlInheritance.generalization("Cible").type());
        assertEquals(UmlInheritanceType.REALIZATION, UmlInheritance.realization("Cible").type());
    }

    @Test
    void deuxHeritagesIdentiques_sontEgaux() {
        assertEquals(UmlInheritance.generalization("Cible"),
            UmlInheritance.generalization("Cible"));
    }

    @Test
    void heritages_seTrientAvecLesAutresRelations() {
        UmlInheritance heritage = UmlInheritance.generalization("pkg.Parent");
        UmlDependency dependance = UmlDependency.toTarget("pkg.Service");

        UmlEntity entity = UmlEntity.builder("pkg.Enfant", "Enfant")
            .relations(ImmutableList.of(heritage, dependance))
            .build();

        assertEquals(ImmutableList.of(heritage), entity.inheritances());
        assertEquals(ImmutableList.of(dependance), entity.dependencies());
        assertEquals(ImmutableList.of(), entity.associations());
    }
}
