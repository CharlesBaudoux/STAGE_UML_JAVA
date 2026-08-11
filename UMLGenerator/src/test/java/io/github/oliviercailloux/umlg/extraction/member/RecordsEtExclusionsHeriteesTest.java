package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

// Deux assouplissements qui réduisent le coût d'annotation, mesurés sur un projet réel :
// onze lignes d'annotations pour trois boîtes triviales de records.
//
// Un composant de record est la définition même du record : il paraît quelle que soit la
// stratégie, NONE comprise — cette stratégie est faite pour choisir parmi des membres
// ordinaires, pas pour taire une définition. Et un @UMLExclude posé sur la déclaration d'une
// interface vaut pour chacune de ses réalisations : le membre s'exclut là où il est déclaré,
// une fois.
class RecordsEtExclusionsHeriteesTest {

    // Sans @UMLClass : les tests d'intégration scannent tout target/test-classes, et
    // MemberExtractor n'en a pas besoin.
    record Mnemonic(String keysymMnemonic) implements Entree {
        @Override
        public String asString() {
            return keysymMnemonic;
        }
    }

    @Test
    void unComposantDeRecord_paraitMemeSousStrategyNone() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Mnemonic.class, UMLVisibility.NONE);

        assertEquals(1, champs.size(), champs.toString());
        assertEquals("keysymMnemonic", champs.get(0).name());
        assertEquals("String", champs.get(0).type());
    }

    record Cache(String visible, @UMLExclude String tu) {}

    @Test
    void unComposantExclu_disparait_leVetoIndividuelGardeLeDernierMot() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Cache.class, UMLVisibility.NONE);

        assertEquals(1, champs.size(), champs.toString());
        assertEquals("visible", champs.get(0).name());
    }

    record AvecConstante(String composant) {
        static final int SEUIL = 3;
    }

    @Test
    void unChampAjouteAuRecord_suitLaStrategieOrdinaire() {
        // Seuls les composants sont la définition du record ; un champ statique ajouté reste un
        // membre comme un autre, que NONE tait.
        List<UmlField> champs =
            MemberExtractor.extractFields(AvecConstante.class, UMLVisibility.NONE);

        assertEquals(1, champs.size(), champs.toString());
        assertEquals("composant", champs.get(0).name());
    }

    // --- L'exclusion posée sur la déclaration, lue depuis les réalisations ---

    interface Entree {
        @UMLExclude
        String asString();
    }

    @Test
    void unExcludeSurLInterface_vautPourLOverride() {
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Mnemonic.class, UMLVisibility.PUBLIC);

        assertTrue(methodes.stream().noneMatch(m -> m.name().equals("asString")),
            "asString est exclue sur la déclaration de Entree ; l'override de Mnemonic doit se "
                + "taire sans répéter l'annotation : " + methodes);
        // Le compagnon de contrôle : l'accesseur du composant, lui, n'est exclu nulle part.
        assertTrue(methodes.stream().anyMatch(m -> m.name().equals("keysymMnemonic")),
            methodes.toString());
    }

    abstract static class Parent {
        @UMLExclude
        public void technique() {}
    }

    static class Fille extends Parent {
        @Override
        public void technique() {}

        public void metier() {}
    }

    @Test
    void unExcludeSurLeParent_vautPourLaFille() {
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Fille.class, UMLVisibility.PUBLIC);

        assertTrue(methodes.stream().noneMatch(m -> m.name().equals("technique")),
            methodes.toString());
        assertTrue(methodes.stream().anyMatch(m -> m.name().equals("metier")),
            methodes.toString());
    }

    static class Homonyme {
        // Statique : elle ne redéfinit rien, l'exclusion d'un homonyme hérité ne la vise pas.
        public static void technique() {}
    }

    @Test
    void uneStatiqueHomonyme_neRedefinitRien_etResteVisible() {
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Homonyme.class, UMLVisibility.PUBLIC);

        assertTrue(methodes.stream().anyMatch(m -> m.name().equals("technique")),
            methodes.toString());
    }
}
