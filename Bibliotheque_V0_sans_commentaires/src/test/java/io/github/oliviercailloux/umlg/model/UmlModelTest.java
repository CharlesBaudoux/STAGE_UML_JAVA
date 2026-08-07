package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

// Les invariants transversaux du modèle : refus des nulls, copies défensives, immuabilité de ce
// qui est exposé, et égalité par valeur.
//
// Ces trois dernières propriétés découlent du choix des records et d'ImmutableList ; les tester
// explicitement les fige, pour qu'un remplacement de ImmutableList par une List ordinaire ne
// passe pas inaperçu.
class UmlModelTest {

    private static UmlEntity entiteMinimale() {
        return new UmlEntity("E", "", "", UMLVisibility.NONE, List.of(), List.of());
    }

    @Test
    void projetVide_estRefuse() {
        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> UmlProject.given(Set.of()));

        assertTrue(e.getMessage().contains("at least one annotated class"), e.getMessage());
    }

    @Test
    void projetNull_estRefuse() {
        assertThrows(NullPointerException.class, () -> UmlProject.given(null));
        assertThrows(NullPointerException.class, () -> new UmlProject(null));
    }

    @Test
    void entitesDUnProjet_sontImmuables() {
        UmlProject projet = UmlProject.given(Set.of(entiteMinimale()));

        assertThrows(UnsupportedOperationException.class,
            () -> projet.entities().add(entiteMinimale()));
    }

    @Test
    void projet_conserveLOrdreDInsertion() {
        UmlEntity a = new UmlEntity("A", "", "", UMLVisibility.NONE, List.of(), List.of());
        UmlEntity b = new UmlEntity("B", "", "", UMLVisibility.NONE, List.of(), List.of());
        UmlEntity c = new UmlEntity("C", "", "", UMLVisibility.NONE, List.of(), List.of());

        UmlProject projet = UmlProject.given(new LinkedHashSet<>(List.of(b, c, a)));

        assertEquals(List.of("B", "C", "A"),
            projet.entities().stream().map(UmlEntity::name).toList());
    }

    @Test
    void projet_neCopiePasInutilementUnImmutableSet() {
        ImmutableSet<UmlEntity> entites = ImmutableSet.of(entiteMinimale());

        assertEquals(entites, UmlProject.given(entites).entities());
    }

    @Test
    void projetsAvecMemesEntites_sontEgaux() {
        assertEquals(UmlProject.given(Set.of(entiteMinimale())),
            UmlProject.given(Set.of(entiteMinimale())));
    }

    @Test
    void entite_refuseChaqueArgumentNull() {
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
    void entite_copieLesListesRecuesALaConstruction() {
        List<UmlField> champs = new ArrayList<>();
        champs.add(new UmlField("a", "int", "+", false));
        List<UmlMethod> methodes = new ArrayList<>();
        methodes.add(new UmlMethod("m", "void", "+", false, List.of()));

        UmlEntity entity = new UmlEntity("E", "", "", UMLVisibility.PUBLIC, champs, methodes);

        champs.clear();
        methodes.clear();

        assertEquals(1, entity.fields().size(),
            "La liste d'attributs ne doit pas suivre la source.");
        assertEquals(1, entity.methods().size(),
            "La liste de méthodes ne doit pas suivre la source.");
    }

    @Test
    void entite_exposeDesListesImmuables() {
        UmlEntity entity = entiteMinimale();

        assertThrows(UnsupportedOperationException.class,
            () -> entity.fields().add(new UmlField("a", "int", "+", false)));
        assertThrows(UnsupportedOperationException.class,
            () -> entity.methods().add(new UmlMethod("m", "void", "+", false, List.of())));
    }

    @Test
    void entitesDifferantParUnSeulChamp_neSontPasEgales() {
        UmlEntity a = new UmlEntity("E", "", "", UMLVisibility.PUBLIC, List.of(), List.of());
        UmlEntity b = new UmlEntity("E", "", "", UMLVisibility.PRIVATE, List.of(), List.of());

        assertNotEquals(a, b, "La stratégie fait partie de l'identité de l'entité.");
    }

    @Test
    void methode_copieSesTypesDeParametres() {
        List<String> parametres = new ArrayList<>(List.of("int", "String"));

        UmlMethod methode = new UmlMethod("calculer", "void", "+", false, parametres);
        parametres.clear();

        assertEquals(List.of("int", "String"), methode.parameterTypes(),
            "La signature ne doit pas suivre la liste passée au constructeur.");
    }

    @Test
    void methode_exposeUneListeDeParametresImmuable() {
        UmlMethod methode = new UmlMethod("calculer", "void", "+", false, List.of("int"));

        assertThrows(UnsupportedOperationException.class,
            () -> methode.parameterTypes().add("long"));
    }

    @Test
    void methodesDeMemeNomMaisDeSignatureDifferente_neSontPasEgales() {
        UmlMethod sans = new UmlMethod("envoyer", "void", "+", false, List.of());
        UmlMethod avec = new UmlMethod("envoyer", "void", "+", false, List.of("String"));

        assertNotEquals(sans, avec,
            "Deux surcharges doivent rester distinctes, sinon un Set les fusionnerait.");
    }

    @Test
    void champsIdentiques_sontEgaux() {
        assertEquals(new UmlField("a", "int", "+", false), new UmlField("a", "int", "+", false));
    }

    @Test
    void champStatiqueEtNonStatique_neSontPasEgaux() {
        assertNotEquals(new UmlField("a", "int", "+", true), new UmlField("a", "int", "+", false));
    }
}
