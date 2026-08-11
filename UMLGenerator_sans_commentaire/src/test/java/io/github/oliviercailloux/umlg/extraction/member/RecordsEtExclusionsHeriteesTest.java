package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordsEtExclusionsHeriteesTest {

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

    record Renomme(@UMLInclude(name = "mnemonic") String keysymMnemonic) {}

    @Test
    void uneInclusionSurUnComposant_renommeLAttribut() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Renomme.class, UMLVisibility.NONE);

        assertEquals(1, champs.size(), champs.toString());
        assertEquals("mnemonic", champs.get(0).name());
    }

    @Test
    void uneInclusionSurUnComposant_nAjoutePasLAccesseur() {
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Renomme.class, UMLVisibility.NONE);

        assertTrue(methodes.isEmpty(), "L'accesseur ne doit pas doubler le composant : "
            + methodes);
    }

    record AccesseurEcrit(String composant) {
        @UMLInclude(name = "lu")
        public String composant() {
            return composant;
        }
    }

    @Test
    void uneInclusionSurUnAccesseurEcritALaMain_estRespectee() {
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(AccesseurEcrit.class, UMLVisibility.NONE);

        assertEquals(1, methodes.size(), methodes.toString());
        assertEquals("lu", methodes.get(0).name());
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
        List<UmlField> champs =
            MemberExtractor.extractFields(AvecConstante.class, UMLVisibility.NONE);

        assertEquals(1, champs.size(), champs.toString());
        assertEquals("composant", champs.get(0).name());
    }

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
