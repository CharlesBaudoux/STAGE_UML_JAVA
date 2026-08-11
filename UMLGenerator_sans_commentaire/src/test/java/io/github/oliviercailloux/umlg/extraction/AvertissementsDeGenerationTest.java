package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AvertissementsDeGenerationTest {

    private static Regles regles(String... lignes) {
        return Regles.lues(ImmutableList.copyOf(lignes), "umlg.rules");
    }

    @UMLClass
    static class Muette {
        public int champ;

        public void faire() {}
    }

    @UMLClass
    static class Marqueur {}

    private static boolean boiteVide(Class<?> clazz, Regles regles) {
        return ProjectScanner.boiteVideFauteDeStrategie(clazz,
            EntityExtractor.extract(clazz, regles), regles);
    }

    @Test
    void sansStrategie_uneBoiteVideEstSignalee() {
        assertTrue(boiteVide(Muette.class, Regles.aucune()));
    }

    @Test
    void strategyNoneEcrit_faitTaireLeSignalement() {
        assertFalse(boiteVide(Muette.class, regles("strategy none")));
    }

    @Test
    void uneStrategieQuiRemplit_nAPlusRienASignaler() {
        assertFalse(boiteVide(Muette.class, regles("strategy public")));
    }

    @Test
    void uneClasseSansMembres_nEstPasSignalee() {
        assertFalse(boiteVide(Marqueur.class, Regles.aucune()));
    }

    @Test
    void leSignalement_nommeLesBoites() {
        assertTrue(ProjectScanner.signalerBoitesVides(ImmutableList.of("Muette")));
        assertFalse(ProjectScanner.signalerBoitesVides(ImmutableList.of()));
    }

    private static UmlEntity entite(UmlMethod... methodes) {
        return UmlEntity.builder("com.exemple.Classe", "Classe")
            .methods(List.of(methodes)).build();
    }

    private static UmlMethod methodeAnonyme() {
        return new UmlMethod("configurer", "void", "+", false, List.of("String"));
    }

    private static UmlMethod methodeNommee() {
        return new UmlMethod("configurer", "void", "", "+", false,
            ImmutableList.of(new UmlParameter("chemin", "String")), false);
    }

    @Test
    void desSignaturesToutesMuettes_sontSignalees() {
        assertTrue(ProjectScanner.signalerParametresAnonymes(
            List.of(entite(methodeAnonyme())), Regles.aucune()));
    }

    @Test
    void unSeulNomPresent_suffitATaireLeSignalement() {
        assertFalse(ProjectScanner.signalerParametresAnonymes(
            List.of(entite(methodeAnonyme()), entite(methodeNommee())), Regles.aucune()));
    }

    @Test
    void desNomsMasquesParLaRegle_neSontPasSignales() {
        assertFalse(ProjectScanner.signalerParametresAnonymes(
            List.of(entite(methodeAnonyme())), regles("omit parameter names")));
    }

    @Test
    void aucunParametreNullePart_neDitRien() {
        assertFalse(ProjectScanner.signalerParametresAnonymes(
            List.of(entite(new UmlMethod("lister", "int", "+", false, List.of()))),
            Regles.aucune()));
    }

    @Test
    void unVoisinageReduitAuCentre_estSignale() {
        UmlEntity seule = UmlEntity.builder("com.exemple.Seule", "Seule").build();
        Set<String> voulus = new LinkedHashSet<>(List.of(seule.qualifiedName()));

        assertTrue(ConstructeurDeVues.signalerVoisinageVide(voulus, ImmutableList.of(seule),
            "Seule", "Autour de Seule"));
    }

    @Test
    void unVoisinageQuiATrouve_neDitRien() {
        UmlEntity centre = UmlEntity.builder("com.exemple.Centre", "Centre").build();
        Set<String> voulus =
            new LinkedHashSet<>(List.of(centre.qualifiedName(), "com.exemple.Voisine"));

        assertFalse(ConstructeurDeVues.signalerVoisinageVide(voulus, ImmutableList.of(centre),
            "Centre", "Autour de Centre"));
    }

    @Test
    void uneRegleTypeMalOrthographiee_estRelevee() {
        Regles lues = regles("type ImutableList -> List");
        lues.nomAffiche(ImmutableList.class);

        assertEquals(List.of("type ImutableList -> List"), lues.reglesSansEffet());
    }

    @Test
    void uneRegleQuiAServi_nEstPasRelevee() {
        Regles lues = regles("type ImmutableList -> List", "hide log");
        lues.nomAffiche(ImmutableList.class);
        assertTrue(lues.membreMasque("log"));

        assertTrue(lues.reglesSansEffet().isEmpty(), lues.reglesSansEffet().toString());
    }

    @Test
    void motifsMorts_sontTousReleves() {
        Regles lues = regles("type ImutableList -> List", "hide get*", "hide LOGGR");
        lues.membreMasque("calculer");
        lues.membreMasque("log");

        assertEquals(List.of("type ImutableList -> List", "hide get*", "hide LOGGR"),
            lues.reglesSansEffet());
    }

    @Test
    void leSignalementDesReglesMortes_neSurgitQueSiUneLigneEstMorte() {
        assertTrue(ProjectScanner.signalerReglesSansEffet(regles("hide LOGGR")));
        assertFalse(ProjectScanner.signalerReglesSansEffet(regles()));
    }

    @Test
    void uneClasseIllisible_estSignalee() {
        assertTrue(ProjectScanner.signalerClassesIllisibles(
            ImmutableList.of("com.exemple.Absente (java.lang.NoClassDefFoundError: com/ex/Parti)")));
    }

    @Test
    void aucuneClasseIllisible_neDitRien() {
        assertFalse(ProjectScanner.signalerClassesIllisibles(ImmutableList.of()));
    }
}
