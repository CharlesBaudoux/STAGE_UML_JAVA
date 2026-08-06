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

    // Fabrique partagée : une entité sans rien, quand seul l'objet importe et pas son contenu.
    private static UmlEntity entiteMinimale() {
        return new UmlEntity("E", "", "", UMLVisibility.NONE, List.of(), List.of());
    }

    // --- UmlProject ---

    @Test
    void projetVide_estRefuse() {
        // Un diagramme sans classe n'a pas de sens : mieux vaut une erreur explicite qu'un
        // SVG blanc.
        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> UmlProject.given(Set.of()));

        // On vérifie aussi le message : c'est lui que lira l'utilisateur, et le second argument
        // d'assertTrue le fait apparaître dans le rapport si le test échoue.
        assertTrue(e.getMessage().contains("at least one annotated class"), e.getMessage());
    }

    @Test
    void projetNull_estRefuse() {
        assertThrows(NullPointerException.class, () -> UmlProject.given(null));
        assertThrows(NullPointerException.class,
            () -> new UmlProject(null, UmlDiagram.defaut()));
    }

    @Test
    void projetSansReglagesDEnsemble_estRefuse() {
        // Le second composant n'est jamais nul : tout le rendu compte sur sa présence, et
        // UmlDiagram.defaut() est là pour dire « rien de particulier » sans avoir à l'omettre.
        assertThrows(NullPointerException.class,
            () -> UmlProject.given(Set.of(entiteMinimale()), null));
    }

    @Test
    void projetSansDiagrammeDeclare_prendLesReglagesParDefaut() {
        // La forme courte de given() est celle qu'emploie un projet sans @UMLDiagram, c'est-à-dire
        // la grande majorité d'entre eux.
        assertEquals(UmlDiagram.defaut(), UmlProject.given(Set.of(entiteMinimale())).diagram());
    }

    @Test
    void entitesDUnProjet_sontImmuables() {
        UmlProject projet = UmlProject.given(Set.of(entiteMinimale()));

        // Une collection immuable de Guava ne refuse pas l'ajout par un booléen : elle lève.
        assertThrows(UnsupportedOperationException.class,
            () -> projet.entities().add(entiteMinimale()));
    }

    @Test
    void projet_conserveLOrdreDInsertion() {
        // ImmutableSet préserve l'ordre : c'est ce qui rend le diagramme reproductible d'un
        // scan à l'autre.
        UmlEntity a = new UmlEntity("A", "", "", UMLVisibility.NONE, List.of(), List.of());
        UmlEntity b = new UmlEntity("B", "", "", UMLVisibility.NONE, List.of(), List.of());
        UmlEntity c = new UmlEntity("C", "", "", UMLVisibility.NONE, List.of(), List.of());

        UmlProject projet = UmlProject.given(new LinkedHashSet<>(List.of(b, c, a)));

        assertEquals(List.of("B", "C", "A"),
            projet.entities().stream().map(UmlEntity::name).toList());
    }

    @Test
    void projet_neCopiePasInutilementUnImmutableSet() {
        // ImmutableSet.copyOf rend l'argument tel quel s'il est déjà immuable : rien à recopier,
        // il ne peut plus changer.
        ImmutableSet<UmlEntity> entites = ImmutableSet.of(entiteMinimale());

        assertEquals(entites, UmlProject.given(entites).entities());
    }

    // Égalité par valeur, fournie par le record : deux projets de même contenu sont le même
    // projet. C'est ce qui rend les assertEquals des autres tests possibles.
    @Test
    void projetsAvecMemesEntites_sontEgaux() {
        assertEquals(UmlProject.given(Set.of(entiteMinimale())),
            UmlProject.given(Set.of(entiteMinimale())));
    }

    // --- UmlEntity ---

    // Chaque paramètre est mis à null tour à tour : un checkNotNull oublié se verrait, alors
    // qu'un seul cas de test ne le montrerait pas.
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
        // Sans copie défensive, l'appelant pourrait modifier le contenu du diagramme après
        // coup, à l'insu de l'entité.
        List<UmlField> champs = new ArrayList<>();
        champs.add(new UmlField("a", "int", "+", false));
        List<UmlMethod> methodes = new ArrayList<>();
        methodes.add(new UmlMethod("m", "void", "+", false, List.of()));

        UmlEntity entity = new UmlEntity("E", "", "", UMLVisibility.PUBLIC, champs, methodes);

        // Le geste du test : on vide les listes d'origine après construction. Sans copie
        // défensive, l'entité se retrouverait vide elle aussi.
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

    // --- UmlMethod ---

    @Test
    void methode_copieSesTypesDeParametres() {
        // Même exigence que pour UmlEntity : la signature ne doit pas pouvoir changer une
        // fois la méthode construite.
        List<String> parametres = new ArrayList<>(List.of("int", "String"));

        UmlMethod methode = new UmlMethod("calculer", "void", "+", false, parametres);
        parametres.clear();

        assertEquals(List.of(UmlParameter.anonyme("int"), UmlParameter.anonyme("String")),
            methode.parameters(),
            "La signature ne doit pas suivre la liste passée au constructeur.");
    }

    @Test
    void methode_exposeUneListeDeParametresImmuable() {
        UmlMethod methode = new UmlMethod("calculer", "void", "+", false, List.of("int"));

        assertThrows(UnsupportedOperationException.class,
            () -> methode.parameters().add(UmlParameter.anonyme("long")));
    }

    @Test
    void methodesDeMemeNomMaisDeSignatureDifferente_neSontPasEgales() {
        UmlMethod sans = new UmlMethod("envoyer", "void", "+", false, List.of());
        UmlMethod avec = new UmlMethod("envoyer", "void", "+", false, List.of("String"));

        assertNotEquals(sans, avec,
            "Deux surcharges doivent rester distinctes, sinon un Set les fusionnerait.");
    }

    // --- UmlField ---

    // UmlField n'a aucun constructeur compact : ces deux tests ne vérifient que l'égalité par
    // valeur engendrée par le record, dont dépendent les comparaisons de listes ailleurs.
    @Test
    void champsIdentiques_sontEgaux() {
        assertEquals(new UmlField("a", "int", "+", false), new UmlField("a", "int", "+", false));
    }

    @Test
    void champStatiqueEtNonStatique_neSontPasEgaux() {
        assertNotEquals(new UmlField("a", "int", "+", true), new UmlField("a", "int", "+", false));
    }
}
