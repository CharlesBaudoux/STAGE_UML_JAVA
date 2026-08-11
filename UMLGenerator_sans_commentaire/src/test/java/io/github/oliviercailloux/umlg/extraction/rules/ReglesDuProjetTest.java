package io.github.oliviercailloux.umlg.extraction.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReglesDuProjetTest {

    private static Regles regles(String... lignes) {
        return Regles.lues(ImmutableList.copyOf(lignes), "les règles du test");
    }

    private static List<String> nomsDesMethodes(UmlEntity entity) {
        return entity.methods().stream().map(UmlMethod::name).toList();
    }

    private static List<String> nomsDesChamps(UmlEntity entity) {
        return entity.fields().stream().map(UmlField::name).toList();
    }

    @UMLClass
    static class SansStrategie {
        public String nom;

        public void valider() {}
    }

    @UMLClass(strategy = UMLVisibility.NONE)
    static class BoiteVideVoulue {
        public String nom;

        public void valider() {}
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class ToutVoulu {
        private String secret;
    }

    @Test
    void sansFichier_uneClasseQuiNeDitRienResteVide() {
        UmlEntity entity = EntityExtractor.extract(SansStrategie.class);

        assertTrue(entity.fields().isEmpty(), entity.fields().toString());
        assertTrue(entity.methods().isEmpty(), entity.methods().toString());
    }

    @Test
    void strategieDuProjet_sAppliqueAuxClassesQuiNeDisentRien() {
        UmlEntity entity = EntityExtractor.extract(SansStrategie.class, regles("strategy public"));

        assertEquals(List.of("nom"), nomsDesChamps(entity));
        assertEquals(List.of("valider"), nomsDesMethodes(entity));
    }

    @Test
    void classeQuiDemandeNone_resteVideMalgreLeProjet() {
        UmlEntity entity =
            EntityExtractor.extract(BoiteVideVoulue.class, regles("strategy public"));

        assertTrue(entity.fields().isEmpty(), entity.fields().toString());
        assertTrue(entity.methods().isEmpty(), entity.methods().toString());
    }

    @Test
    void classeQuiDeclareSaStrategie_lEmporteSurLeProjet() {
        UmlEntity entity = EntityExtractor.extract(ToutVoulu.class, regles("strategy public"));

        assertEquals(List.of("secret"), nomsDesChamps(entity),
            "PRIVATE sur la classe doit l'emporter sur « strategy public ».");
    }

    @Test
    void strategieInconnue_estRefusee() {
        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> regles("strategy publique"));

        assertTrue(e.getMessage().contains("unknown strategy"), e.getMessage());
    }

    @Test
    void strategieProject_estRefusee() {
        assertThrows(IllegalArgumentException.class, () -> regles("strategy project"));
    }

    @Test
    void deuxStrategies_sontRefusees() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> regles("strategy none", "strategy public"));

        assertTrue(e.getMessage().contains("already set"), e.getMessage());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Bavarde {
        private String logger;
        private String nom;

        public String getNom() { return nom; }

        public void setNom(String v) { nom = v; }

        public Bavarde withNom(String v) { return this; }

        public void valider() {}

        @Override
        public String toString() { return ""; }
    }

    @Test
    void sansRegle_plusRienNestMasque() {
        List<String> methodes = nomsDesMethodes(EntityExtractor.extract(Bavarde.class));

        assertTrue(methodes.contains("getNom"), methodes.toString());
        assertTrue(methodes.contains("toString"), methodes.toString());
    }

    @Test
    void motifDAccesseurs_masqueLesGettersEtLesSetters() {
        List<String> methodes = nomsDesMethodes(
            EntityExtractor.extract(Bavarde.class, regles("hide get*, set*")));

        assertFalse(methodes.contains("getNom"), methodes.toString());
        assertFalse(methodes.contains("setNom"), methodes.toString());
        assertTrue(methodes.contains("valider"), "La méthode métier reste.");
        assertTrue(methodes.contains("toString"),
            "Ce qu'aucun motif ne vise reste affiché.");
    }

    @Test
    void motifExact_neMasqueQueLeNomEcrit() {
        List<String> methodes =
            nomsDesMethodes(EntityExtractor.extract(Bavarde.class, regles("hide toString")));

        assertFalse(methodes.contains("toString"), methodes.toString());
        assertTrue(methodes.contains("getNom"), methodes.toString());
    }

    @Test
    void motifDeMethode_masqueLesConventionsDuProjet() {
        List<String> methodes =
            nomsDesMethodes(EntityExtractor.extract(Bavarde.class, regles("hide with*")));

        assertFalse(methodes.contains("withNom"), methodes.toString());
        assertTrue(methodes.contains("valider"), methodes.toString());
    }

    @Test
    void plusieursMotifs_seSeparentParDesVirgules() {
        List<String> methodes = nomsDesMethodes(
            EntityExtractor.extract(Bavarde.class, regles("hide with*, valider")));

        assertFalse(methodes.contains("withNom"), methodes.toString());
        assertFalse(methodes.contains("valider"), methodes.toString());
    }

    @Test
    void motifDeChamp_masqueUnAttribut() {
        List<String> champs =
            nomsDesChamps(EntityExtractor.extract(Bavarde.class, regles("hide log*")));

        assertFalse(champs.contains("logger"), champs.toString());
        assertTrue(champs.contains("nom"), champs.toString());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class RepecheeALaMain {
        @UMLInclude
        private String logger;

        @UMLInclude
        public String getNom() { return ""; }
    }

    @Test
    void includeLEmporteSurLaRegle() {
        UmlEntity entity = EntityExtractor.extract(RepecheeALaMain.class,
            regles("hide get*", "hide log*"));

        assertTrue(nomsDesChamps(entity).contains("logger"), entity.fields().toString());
        assertTrue(nomsDesMethodes(entity).contains("getNom"), entity.methods().toString());
    }

    @Test
    void motifQuiNeViseRien_seReleveEnFinDeScan() {
        Regles lues = regles("hide accesseurs");

        assertEquals(List.of("hide accesseurs"), lues.reglesSansEffet());
    }

    @Test
    void hideSansMotif_estRefuse() {
        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> regles("hide"));

        assertTrue(e.getMessage().contains("hide <patterns>"), e.getMessage());
    }

    @Test
    void motifToutSeul_estRefuse() {
        assertThrows(IllegalArgumentException.class, () -> regles("hide *"));
    }

    @Test
    void motifVide_estRefuse() {
        assertThrows(IllegalArgumentException.class, () -> regles("hide get*,"));
    }

    @Test
    void parDefaut_lesVisibilitesSontEcrites() {
        assertEquals("-", EntityExtractor.extract(Bavarde.class).fields().stream()
            .filter(f -> f.name().equals("nom")).findFirst().orElseThrow().visibility());
    }

    @Test
    void omitVisibility_lesTait() {
        UmlEntity entity = EntityExtractor.extract(Bavarde.class, regles("omit visibility"));

        assertTrue(entity.fields().stream().allMatch(f -> f.visibility().isEmpty()),
            entity.fields().toString());
        assertTrue(entity.methods().stream().allMatch(m -> m.visibility().isEmpty()),
            entity.methods().toString());
    }

    @Test
    void omitVisibility_neLaisseAucunEspaceDansLeDessin() {
        String source = io.github.oliviercailloux.umlg.rendering.PlantUmlAssert.documentPour(
            EntityExtractor.extract(Bavarde.class, regles("omit visibility")));

        assertTrue(source.contains("  nom : String"), source);
        assertFalse(source.contains("   nom"), "Pas d'espace surnuméraire : " + source);
    }

    @Test
    void omitVisibility_deuxFois_estRefuse() {
        assertThrows(IllegalArgumentException.class,
            () -> regles("omit visibility", "omit visibility"));
    }

    @UMLClass
    static class Rangee {}

    @UMLClass
    @UMLPackage(name = "Écrit à la main")
    static class DejaRangee {}

    private static final String PAQUETAGE = ReglesDuProjetTest.class.getPackageName();

    @Test
    void groupPackage_nExistePlus() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> regles("group package " + PAQUETAGE + " -> \"Règles\""));

        assertTrue(e.getMessage().contains("unknown rule"), e.getMessage());
    }

    @Test
    void sansAnnotation_aucuneRegleNeRangeUneClasse() {
        UmlEntity entity = EntityExtractor.extract(Rangee.class, regles("strategy public"));

        assertFalse(entity.group().estGroupee(), entity.group().toString());
    }

    @Test
    void lAnnotationRangeLaClasse() {
        UmlEntity entity = EntityExtractor.extract(DejaRangee.class, regles("strategy public"));

        assertEquals("Écrit à la main", entity.group().name());
    }

    @Test
    void fichierComplet_estLuSansErreur() {
        Regles lues = regles(
            "# Les réglages de tout le projet",
            "strategy public",
            "",
            "hide get*, set*",
            "hide with*, visit*, log, LOGGER",
            "omit return void",
            "omit visibility",
            "type ImmutableList -> List");

        assertFalse(lues.vides());
        assertEquals(UMLVisibility.PUBLIC, lues.strategie());
        assertTrue(lues.membreMasque("getNom"));
        assertTrue(lues.membreMasque("withNom"));
        assertTrue(lues.membreMasque("LOGGER"));
        assertTrue(lues.omettreVisibilite());
        assertEquals("", lues.nomAfficheRetour(void.class));
        assertEquals("List", lues.nomAffiche(com.google.common.collect.ImmutableList.class));
    }

    @Test
    void fichierVide_neRegleRien() {
        assertTrue(regles().vides());
        assertEquals(UMLVisibility.NONE, regles().strategie());
    }
}
