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

// Deux attributs qui se font face ne sont qu'une association.
//
// Le modèle en garde deux, parce qu'il dit ce que chaque classe déclare ; le dessin n'en veut
// qu'un, à deux extrémités nommées, parce que c'est la définition UML d'une association binaire.
// Ces tests fixent où la fusion s'arrête : elle ne doit jamais perdre en silence ce que le
// second lien dit de plus.
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
            .targetRole("bs").targetCardinality("0..*").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("a").targetCardinality("1").build());

        assertEquals(1, relationsDe("A", a, b).size());
        assertTrue(relationsDe("B", a, b).isEmpty(),
            "Le second lien est absorbé par le premier, pas dessiné à côté.");
    }

    @Test
    void leTraitFusionne_porteLesDeuxRoles() {
        // Le rôle d'une extrémité est le nom sous lequel l'autre bout la désigne : « B.a » nomme
        // donc l'extrémité A du trait, et c'est ce que la fusion doit produire.
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B")
            .targetRole("bs").targetCardinality("0..*").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("a").targetCardinality("1").build());

        UmlAssociation trait = (UmlAssociation) relationsDe("A", a, b).get(0);

        assertEquals("a", trait.sourceRole());
        assertEquals("1", trait.sourceCardinality());
        assertEquals("bs", trait.targetRole());
        assertEquals("0..*", trait.targetCardinality());
    }

    @Test
    void unLosangeDeLautreCote_empecheLaFusion() {
        // Le losange d'une composition se place à la source du lien qui le déclare. Le reporter
        // sur l'extrémité opposée du trait conservé le mettrait du mauvais côté : mieux vaut
        // deux traits qu'un trait faux.
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        assertEquals(1, relationsDe("A", a, b).size());
        assertEquals(1, relationsDe("B", a, b).size());
    }

    @Test
    void uneContrainteDeLautreCote_empecheLaFusion() {
        // Une extrémité source ne sait porter qu'un rôle, une multiplicité et un qualificatif :
        // la contrainte du second lien n'aurait nulle part où aller, et disparaîtrait.
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .constraints(List.of("non vide")).build());

        assertEquals(1, relationsDe("A", a, b).size());
        assertEquals(1, relationsDe("B", a, b).size());
    }

    @Test
    void unCoteSourceDejaEcrit_estRespecte() {
        // L'utilisateur a déjà dit ce que porte l'extrémité source ; ce qu'il a écrit l'emporte
        // sur ce que la classe d'en face en dirait.
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B")
            .sourceRole("proprietaire").sourceCardinality("1").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("autre").build());

        assertEquals(1, relationsDe("A", a, b).size());
        assertEquals(1, relationsDe("B", a, b).size());
    }

    @Test
    void deuxAttributsVersLaMemeCible_restentDeuxTraits() {
        // Une seule classe d'en face, mais deux propriétés distinctes : ce sont bien deux
        // associations, et la seconde n'a pas de réciproque à elle.
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

    // --- Le document réellement engendré, et non le calcul seul ---

    @Test
    void leDocumentEngendre_neContientQuUnTrait() {
        // Le test qui manquait, et dont l'absence a laissé passer une dérive silencieuse.
        //
        // Les tests précédents interrogent AssociationsReciproques ; celui-ci passe par le
        // document, donc par la même séquence de passes que PlantUmlWriter. Tant que le helper
        // des tests recopiait cette séquence au lieu de l'appeler, il écrivait l'ancien document
        // — deux traits — sans qu'aucun test ne s'en aperçoive.
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B")
            .targetRole("bs").targetCardinality("0..*").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .targetRole("a").targetCardinality("1").build());

        List<String> traits = PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(a, b));

        assertEquals(1, traits.size(), "Un seul trait attendu, obtenu : " + traits);
        assertTrue(traits.get(0).contains("\"a 1\""), traits.get(0));
        assertTrue(traits.get(0).contains("\"bs 0..*\""), traits.get(0));
    }

    @Test
    void leDocumentEngendre_gardeLesDeuxTraitsQuandLaFusionPerdrait() {
        // Le pendant du précédent : ce que la fusion refuse doit se voir sur le dessin aussi.
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B").build());
        UmlEntity b = entite("B", UmlAssociation.builder("a", "dm.A")
            .type(UMLRelationType.COMPOSITION).build());

        assertEquals(2, PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(a, b)).size());
    }

    // --- Ce que dit l'avertissement de non-fusion ---
    //
    // Il n'énonçait qu'une des deux causes, et son remède — « ne décorez qu'un seul côté » —
    // était inapplicable à l'autre : quand le lien conservé a déjà écrit son propre côté source,
    // retirer les décorations du partenaire ne change rien au dessin.

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
        // Le cas que le message taisait : rien n'est décoré, et pourtant la fusion n'a pas lieu.
        // Le conseil « ne décorez qu'un seul côté » n'y avait aucune prise.
        UmlEntity a = entite("A", UmlAssociation.builder("bs", "dm.B")
            .sourceRole("proprietaire").sourceCardinality("1").build());
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
        // N'en corriger qu'une laisserait les deux traits, sans qu'on sache pourquoi.
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
        // Une classe qui se référence elle-même n'a pas de « face » distincte : le trait
        // reviendrait sur lui-même, et il n'y a rien à absorber.
        UmlEntity a = entite("A", UmlAssociation.builder("parent", "dm.A").build());

        assertEquals(1, relationsDe("A", a).size());
    }
}
