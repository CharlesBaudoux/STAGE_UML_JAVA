package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;
import org.junit.jupiter.api.Test;

// Le stockage des relations dans UmlEntity : une seule liste pour les deux natures, filtrée à
// la demande. Ces tests fixent le comportement dont dépend le rendu — ordre conservé, ajouts
// cumulés, liste immuable.
class UmlEntityRelationsTest {

    // Trois relations témoins, réutilisées partout : une association et deux dépendances, ce qui
    // suffit à vérifier le filtrage par nature comme l'ordre.
    private static final UmlAssociation DETENUE =
        UmlAssociation.builder("champ", "pkg.Cible").build();
    private static final UmlDependency APPELLE = new UmlDependency("pkg.Service", "appelle");
    private static final UmlDependency TRACE = new UmlDependency("pkg.Journal", "trace");

    // Rend un builder neuf à chaque appel : les tests ne peuvent pas se contaminer entre eux.
    private static UmlEntity.Builder entite() {
        return UmlEntity.builder("pkg.Source", "Source");
    }

    @Test
    void entiteNue_nAAucunLien() {
        assertTrue(entite().build().relations().isEmpty());
    }

    @Test
    void lesDeuxNatures_cohabitentDansUneSeuleListe() {
        UmlEntity entity = entite().relations(List.of(DETENUE, APPELLE)).build();

        assertEquals(List.of(DETENUE, APPELLE), entity.relations());
    }

    // Le filtre de relations(Class) : chaque vue ne rend que sa nature, dans l'ordre d'origine.
    @Test
    void vueParNature_neRetientQueLaSienne() {
        UmlEntity entity = entite().relations(List.of(DETENUE, APPELLE, TRACE)).build();

        assertEquals(List.of(DETENUE), entity.relations(UmlAssociation.class));
        assertEquals(List.of(APPELLE, TRACE), entity.relations(UmlDependency.class));
    }

    // associations() et dependencies() ne sont que des raccourcis vers relations(Class) : rien
    // ne doit diverger entre les deux écritures.
    @Test
    void vuesNommees_sontDesFiltresEquivalents() {
        UmlEntity entity = entite().relations(List.of(DETENUE, APPELLE)).build();

        assertEquals(entity.relations(UmlAssociation.class), entity.associations());
        assertEquals(entity.relations(UmlDependency.class), entity.dependencies());
    }

    @Test
    void appelsSuccessifs_sAjoutentAuLieuDeSeRemplacer() {
        // Les liens d'une entité proviennent d'autant de sources qu'il y a d'annotations
        // reconnues : aucune ne doit effacer le travail des autres.
        UmlEntity entity = entite()
            .associations(List.of(DETENUE))
            .dependencies(List.of(APPELLE))
            .build();

        assertEquals(List.of(DETENUE, APPELLE), entity.relations());
    }

    @Test
    void ordreDeDeclaration_estConserve() {
        // C'est cet ordre qui décide de celui des traits dans le document produit.
        UmlEntity entity = entite().relations(List.of(TRACE, DETENUE, APPELLE)).build();

        assertEquals(List.of(TRACE, DETENUE, APPELLE), entity.relations());
    }

    @Test
    void listeDeLiens_estImmuable() {
        UmlEntity entity = entite().relations(List.of(DETENUE)).build();

        assertThrows(UnsupportedOperationException.class, () -> entity.relations().clear());
    }

    @Test
    void listeRecue_nEstPasSuivieApresConstruction() {
        // Sans copie défensive, l'appelant pourrait modifier le contenu du diagramme après coup.
        List<UmlRelation> source = new java.util.ArrayList<>(List.of(DETENUE, APPELLE));

        UmlEntity entity = entite().relations(source).build();
        source.clear();

        assertEquals(2, entity.relations().size());
    }

    @Test
    void lienNull_estRefuse() {
        // singletonList accepte null, contrairement à List.of : c'est le seul moyen de fabriquer
        // une liste contenant un null pour éprouver la garde.
        assertThrows(NullPointerException.class,
            () -> entite().relations(java.util.Collections.singletonList(null)));
        assertThrows(NullPointerException.class, () -> entite().relations(null));
    }

    // Le constructeur à six arguments passe ImmutableList.of() pour les relations : une entité
    // ainsi construite n'a pas de relations, et surtout pas null.
    @Test
    void constructeurRaccourci_neDeclareAucunLien() {
        UmlEntity entity =
            new UmlEntity("E", "", "", UMLVisibility.NONE, List.of(), List.of());

        assertTrue(entity.relations().isEmpty());
    }
}
