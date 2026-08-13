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
        assertThrows(NullPointerException.class,
            () -> new UmlProject(null, UmlDiagram.defaults(), ImmutableList.of()));
    }

    @Test
    void projetSansReglagesDEnsemble_estRefuse() {
        assertThrows(NullPointerException.class,
            () -> UmlProject.given(Set.of(entiteMinimale()), null));
    }

    @Test
    void projetSansDiagrammeDeclare_prendLesReglagesParDefaut() {
        assertEquals(UmlDiagram.defaults(), UmlProject.given(Set.of(entiteMinimale())).diagram());
    }


    @Test
    void deuxEntitesDeMemeNomQualifie_sontRefusees() {
        UmlEntity bleue = UmlEntity.builder("com.banque.Compte", "Compte").color("#DDEEFF").build();
        UmlEntity rouge = UmlEntity.builder("com.banque.Compte", "Compte").color("#FFDDDD").build();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> UmlProject.given(new LinkedHashSet<>(List.of(bleue, rouge))));

        assertTrue(e.getMessage().contains("com.banque.Compte"),
            "Le message doit nommer le doublon, seule chose à corriger : " + e.getMessage());
    }

    @Test
    void deuxEntitesDeNomsQualifiesDistincts_cohabitent() {
        UmlEntity ici = UmlEntity.builder("com.banque.Compte", "Compte").build();
        UmlEntity ailleurs = UmlEntity.builder("com.clients.Compte", "Compte").build();

        assertEquals(2, UmlProject.given(new LinkedHashSet<>(List.of(ici, ailleurs)))
            .entities().size());
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
    void entite_refuseUnNomVide() {
        assertThrows(IllegalArgumentException.class,
            () -> new UmlEntity("", "", "", UMLVisibility.NONE, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new UmlEntity("   ", "", "", UMLVisibility.NONE, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> UmlEntity.builder("com.banque.Compte", "  ").build());
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

        assertEquals(List.of(UmlParameter.unnamed("int"), UmlParameter.unnamed("String")),
            methode.parameters(),
            "La signature ne doit pas suivre la liste passée au constructeur.");
    }

    @Test
    void methode_exposeUneListeDeParametresImmuable() {
        UmlMethod methode = new UmlMethod("calculer", "void", "+", false, List.of("int"));

        assertThrows(UnsupportedOperationException.class,
            () -> methode.parameters().add(UmlParameter.unnamed("long")));
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


    @Test
    void champSansNom_estRefuse() {
        assertThrows(NullPointerException.class, () -> new UmlField(null, "int", "+", false));
        assertThrows(IllegalArgumentException.class, () -> new UmlField("  ", "int", "+", false));
    }

    @Test
    void champSansType_estRefuse() {
        assertThrows(NullPointerException.class, () -> new UmlField("a", null, "+", false));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new UmlField("a", "", "+", false));
        assertTrue(e.getMessage().contains("a"),
            "Le message doit nommer l'attribut fautif : " + e.getMessage());
    }

    @Test
    void champDeVisibiliteInconnue_estRefuse() {
        assertThrows(NullPointerException.class, () -> new UmlField("a", "int", null, false));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new UmlField("a", "int", "?", false));
        assertTrue(e.getMessage().contains("?"), e.getMessage());
    }

    @Test
    void champSansVisibiliteEcrite_estAccepte() {
        assertEquals("", new UmlField("a", "int", "", false).visibility());
    }


    @Test
    void methodeSansNom_estRefusee() {
        assertThrows(NullPointerException.class,
            () -> new UmlMethod(null, "void", "+", false, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new UmlMethod(" ", "void", "+", false, List.of()));
    }

    @Test
    void methodeDeVisibiliteInconnue_estRefusee() {
        assertThrows(IllegalArgumentException.class,
            () -> new UmlMethod("m", "void", "?", false, List.of()));
    }

    @Test
    void methodeSansTypeDeRetourEcrit_estAcceptee() {
        assertEquals("", new UmlMethod("m", "", "+", false, List.of()).returnType());
    }

    @Test
    void methodeDontLeTypeDeRetourNEstQueDesEspaces_estRefusee() {
        assertThrows(IllegalArgumentException.class,
            () -> new UmlMethod("m", " ", "+", false, List.of()));
    }
}
