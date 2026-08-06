package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Vérifie les gardes du record UmlInheritance, et sa place parmi les relations.
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

    // Un lien d'héritage ne porte jamais de texte : le contrat d'UmlRelation impose la méthode,
    // la chaîne vide dit l'absence. RelationRenderer n'écrit alors rien après le trait.
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

    // Un record : deux liens d'héritage de même cible et de même nature sont le même objet.
    @Test
    void deuxHeritagesIdentiques_sontEgaux() {
        assertEquals(UmlInheritance.generalization("Cible"),
            UmlInheritance.generalization("Cible"));
    }

    // La relation doit se laisser trier par UmlEntity.relations(Class) comme les deux autres :
    // c'est ce qui permet aux accesseurs spécialisés de cohabiter dans une liste unique.
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
