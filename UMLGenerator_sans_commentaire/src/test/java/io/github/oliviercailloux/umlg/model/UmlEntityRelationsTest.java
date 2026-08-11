package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;
import org.junit.jupiter.api.Test;

class UmlEntityRelationsTest {

    private static final UmlAssociation DETENUE =
        UmlAssociation.builder("champ", "pkg.Cible").build();
    private static final UmlDependency APPELLE = new UmlDependency("pkg.Service", "appelle");
    private static final UmlDependency TRACE = new UmlDependency("pkg.Journal", "trace");

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

    @Test
    void vueParNature_neRetientQueLaSienne() {
        UmlEntity entity = entite().relations(List.of(DETENUE, APPELLE, TRACE)).build();

        assertEquals(List.of(DETENUE), entity.relations(UmlAssociation.class));
        assertEquals(List.of(APPELLE, TRACE), entity.relations(UmlDependency.class));
    }

    @Test
    void vuesNommees_sontDesFiltresEquivalents() {
        UmlEntity entity = entite().relations(List.of(DETENUE, APPELLE)).build();

        assertEquals(entity.relations(UmlAssociation.class), entity.associations());
        assertEquals(entity.relations(UmlDependency.class), entity.dependencies());
    }

    @Test
    void appelsSuccessifs_sAjoutentAuLieuDeSeRemplacer() {
        UmlEntity entity = entite()
            .associations(List.of(DETENUE))
            .dependencies(List.of(APPELLE))
            .build();

        assertEquals(List.of(DETENUE, APPELLE), entity.relations());
    }

    @Test
    void ordreDeDeclaration_estConserve() {
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
        List<UmlRelation> source = new java.util.ArrayList<>(List.of(DETENUE, APPELLE));

        UmlEntity entity = entite().relations(source).build();
        source.clear();

        assertEquals(2, entity.relations().size());
    }

    @Test
    void lienNull_estRefuse() {
        assertThrows(NullPointerException.class,
            () -> entite().relations(java.util.Collections.singletonList(null)));
        assertThrows(NullPointerException.class, () -> entite().relations(null));
    }

    @Test
    void constructeurRaccourci_neDeclareAucunLien() {
        UmlEntity entity =
            new UmlEntity("E", "", "", UMLVisibility.NONE, List.of(), List.of());

        assertTrue(entity.relations().isEmpty());
    }
}
