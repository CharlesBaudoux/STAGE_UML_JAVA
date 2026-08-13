package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssociationsReciproquesTest {

    private static UmlEntity entite(String nom, UmlRelation... relations) {
        return UmlEntity.builder("dm." + nom, nom).relations(List.of(relations)).build();
    }

    private static ImmutableList<UmlRelation> relationsDe(String nom, UmlEntity... entites) {
        ImmutableMap<String, ImmutableList<UmlRelation>> fusionnees =
            AssociationsReciproques.de(List.of(entites));
        return fusionnees.get("dm." + nom);
    }

    @Test
    void deuxAttributsQuiSeFontFace_neDonnentQuUnSeulTrait() {
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B")
            .targetRole("bs").targetMultiplicity("0..*").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("a").targetMultiplicity("1").build());

        assertEquals(1, relationsDe("A", a, b).size());
        assertTrue(relationsDe("B", a, b).isEmpty(),
            "Le second lien est absorbé par le premier, pas dessiné à côté.");
    }

    @Test
    void leTraitFusionne_porteLesDeuxRoles() {
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B")
            .targetRole("bs").targetMultiplicity("0..*").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("a").targetMultiplicity("1").build());

        UmlAssociation trait = (UmlAssociation) relationsDe("A", a, b).get(0);

        assertEquals("a", trait.sourceRole());
        assertEquals("1", trait.sourceMultiplicity());
        assertEquals("bs", trait.targetRole());
        assertEquals("0..*", trait.targetMultiplicity());
    }

    @Test
    void unLosangeDeLautreCote_empecheLaFusion() {
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        assertEquals(1, relationsDe("A", a, b).size());
        assertEquals(1, relationsDe("B", a, b).size());
    }

    @Test
    void uneContrainteDeLautreCote_empecheLaFusion() {
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .constraints(List.of("non vide")).build());

        assertEquals(1, relationsDe("A", a, b).size());
        assertEquals(1, relationsDe("B", a, b).size());
    }

    @Test
    void unCoteSourceDejaEcrit_estRespecte() {
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B")
            .sourceRole("proprietaire").sourceMultiplicity("1").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("autre").build());

        assertEquals(1, relationsDe("A", a, b).size());
        assertEquals(1, relationsDe("B", a, b).size());
    }

    @Test
    void deuxAttributsVersLaMemeCible_restentDeuxTraits() {
        UmlEntity a = entite("A",
            UmlAssociation.builder("courante", "dm.B").build(),
            UmlAssociation.builder("archivees", "dm.B").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A").build());

        assertEquals(2, relationsDe("A", a, b).size());
        assertTrue(relationsDe("B", a, b).isEmpty());
    }

    @Test
    void uneCibleHorsDuDiagramme_neChangeRien() {
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.Absente").build());

        assertEquals(1, relationsDe("A", a).size());
    }


    @Test
    void leDocumentEngendre_neContientQuUnTrait() {
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B")
            .targetRole("bs").targetMultiplicity("0..*").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("a").targetMultiplicity("1").build());

        List<String> traits = PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(a, b));

        assertEquals(1, traits.size(), "Un seul trait attendu, obtenu : " + traits);
        assertTrue(traits.get(0).contains("\"a 1\""), traits.get(0));
        assertTrue(traits.get(0).contains("\"bs 0..*\""), traits.get(0));
    }

    @Test
    void leDocumentEngendre_gardeLesDeuxTraitsQuandLaFusionPerdrait() {
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        assertEquals(2, PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(a, b)).size());
    }


    private static String avertissementPour(UmlEntity... entites) {
        PrintStream avant = System.err;
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(tampon, true, StandardCharsets.UTF_8));
            AssociationsReciproques.de(List.of(entites));
        } finally {
            System.setErr(avant);
        }
        return tampon.toString(StandardCharsets.UTF_8);
    }

    @Test
    void partenaireDecore_estLaCauseAnnoncee() {
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        String dit = avertissementPour(a, b);

        assertTrue(dit.contains("dm.B.a carries more than a role"), dit);
        assertTrue(dit.contains("Keep the decorations on one side only"), dit);
        assertFalse(dit.contains("source end"),
            "Cette cause-là ne s'applique pas ici : " + dit);
    }

    @Test
    void coteSourceDejaEcrit_estLautreCauseEtSonRemedePropre() {
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B")
            .sourceRole("proprietaire").sourceMultiplicity("1").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("autre").build());

        String dit = avertissementPour(a, b);

        assertTrue(dit.contains("dm.A.bs already names its own source end"), dit);
        assertTrue(dit.contains("Drop that source end"), dit);
        assertFalse(dit.contains("Keep the decorations on one side only"),
            "Un remède inapplicable ne doit plus être proposé : " + dit);
    }

    @Test
    void lesDeuxCauses_sontEnonceesEnsemble() {
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B")
            .sourceRole("proprietaire").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        String dit = avertissementPour(a, b);

        assertTrue(dit.contains("carries more than a role"), dit);
        assertTrue(dit.contains("already names its own source end"), dit);
    }

    @Test
    void uneAssociationVersSoiMeme_nEstPasFusionnee() {
        UmlEntity a = entite("A", UmlAssociation.builder("parent", "dm.A").build());

        assertEquals(1, relationsDe("A", a).size());
    }
}
