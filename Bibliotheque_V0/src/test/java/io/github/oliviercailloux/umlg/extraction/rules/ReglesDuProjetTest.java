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

// Les trois réglages que le fichier de règles apporte au projet entier : la stratégie par
// défaut, le filtre anti-bruit, et le rangement d'un paquetage dans un rectangle.
//
// Ce qu'ils ont en commun est ce qui justifie de les éprouver ensemble : chacun remplace une
// annotation qu'il fallait autrement répéter sur chaque classe, et chacun cède devant
// l'annotation quand elle est écrite. C'est cette seconde moitié — la règle perd contre le cas
// particulier — qui compte le plus, et qui est vérifiée à chaque fois.
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

    // --- La stratégie du projet ---

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
        // Le comportement historique, préservé : un projet sans fichier de règles se comporte
        // exactement comme avant l'existence du réglage.
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
        // Le cas qui justifie à lui seul l'existence de UMLVisibility.PROJECT : sans lui, « la
        // classe n'a rien dit » et « la classe demande NONE » seraient indiscernables, et cette
        // boîte se remplirait contre la volonté de son auteur.
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
        // « project » est ce qu'une classe écrit pour renvoyer à cette ligne : la ligne ne peut
        // pas se renvoyer à elle-même.
        assertThrows(IllegalArgumentException.class, () -> regles("strategy project"));
    }

    @Test
    void deuxStrategies_sontRefusees() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> regles("strategy none", "strategy public"));

        assertTrue(e.getMessage().contains("already set"), e.getMessage());
    }

    // --- Le filtre anti-bruit ---

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
        // Le changement de fond : la bibliothèque ne décide plus de ce qui mérite d'être dessiné.
        // Un projet qui ne dit rien voit tout ce que sa stratégie autorise, accesseurs compris.
        List<String> methodes = nomsDesMethodes(EntityExtractor.extract(Bavarde.class));

        assertTrue(methodes.contains("getNom"), methodes.toString());
        assertTrue(methodes.contains("toString"), methodes.toString());
    }

    @Test
    void hideAccessors_masqueLesTroisFormes() {
        List<String> methodes =
            nomsDesMethodes(EntityExtractor.extract(Bavarde.class, regles("hide accessors")));

        assertFalse(methodes.contains("getNom"), methodes.toString());
        assertFalse(methodes.contains("setNom"), methodes.toString());
        assertTrue(methodes.contains("valider"), "La méthode métier reste.");
        assertTrue(methodes.contains("toString"),
            "Une autre famille n'est pas masquée pour autant.");
    }

    @Test
    void hideObjectMethods_neMasquePasLesAccesseurs() {
        // Les familles sont indépendantes : demander l'une ne demande pas les autres.
        List<String> methodes =
            nomsDesMethodes(EntityExtractor.extract(Bavarde.class, regles("hide object-methods")));

        assertFalse(methodes.contains("toString"), methodes.toString());
        assertTrue(methodes.contains("getNom"), methodes.toString());
    }

    @Test
    void motifDeMethode_masqueCeQuAucuneFamilleNeReconnait() {
        // « withNom » n'est ni un accesseur ni une méthode d'Object : aucune famille ne le voit.
        // C'est exactement ce que les motifs viennent couvrir — les conventions propres au projet.
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
        // Le manque le plus criant de l'ancien filtre : il ne regardait que les méthodes, si bien
        // qu'un logger ne se cachait qu'avec un @UMLExclude écrit à la main sur chaque classe. Le
        // motif est le même que pour une méthode : la ligne ne distingue pas les deux sortes.
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
        // La règle de priorité : le cas particulier écrit sur le membre bat le réglage général.
        UmlEntity entity = EntityExtractor.extract(RepecheeALaMain.class,
            regles("hide accessors", "hide log*"));

        assertTrue(nomsDesChamps(entity).contains("logger"), entity.fields().toString());
        assertTrue(nomsDesMethodes(entity).contains("getNom"), entity.methods().toString());
    }

    @Test
    void familleMalOrthographiee_estUnMotif_etSeReleveEnFinDeScan() {
        // Depuis que « hide » prend directement des motifs, « accesseurs » est un nom de membre
        // parfaitement licite : rien ne distingue la faute de frappe de l'intention. Le refus est
        // donc impossible, et c'est le relevé des règles mortes qui prend le relais — le même
        // filet que pour « hide LOGGR » ou l'étoile oubliée d'un « hide get ».
        Regles lues = regles("hide accesseurs");

        assertFalse(lues.masque(Bruit.ACCESSORS));
        assertEquals(List.of("hide accesseurs"), lues.reglesSansEffet());
    }

    @Test
    void uneFamille_resteReconnueAvantLesMotifs() {
        // L'ordre de lecture compte : sans lui, « accessors » passerait pour un nom de membre.
        assertTrue(regles("hide accessors").masque(Bruit.ACCESSORS));
    }

    @Test
    void familleDemandeeDeuxFois_estRefusee() {
        assertThrows(IllegalArgumentException.class,
            () -> regles("hide accessors", "hide accessors"));
    }

    @Test
    void motifToutSeul_estRefuse() {
        // « * » masquerait tout le compartiment : licite en théorie, assez inattendu pour qu'on
        // refuse de le deviner.
        assertThrows(IllegalArgumentException.class, () -> regles("hide *"));
    }

    @Test
    void motifVide_estRefuse() {
        assertThrows(IllegalArgumentException.class, () -> regles("hide get*,"));
        assertThrows(IllegalArgumentException.class, () -> regles("hide"));
    }

    // --- Les symboles de visibilité ---

    @Test
    void parDefaut_lesVisibilitesSontEcrites() {
        // UML les demande, et elles disent quelque chose qu'aucune autre marque ne dit.
        assertEquals("-", EntityExtractor.extract(Bavarde.class).fields().stream()
            .filter(f -> f.name().equals("nom")).findFirst().orElseThrow().visibility());
    }

    @Test
    void omitVisibility_lesTait() {
        // Beaucoup de diagrammes tenus à la main les omettent, parce qu'ils montrent une
        // structure plutôt qu'une API. Le projet doit pouvoir en faire autant.
        UmlEntity entity = EntityExtractor.extract(Bavarde.class, regles("omit visibility"));

        assertTrue(entity.fields().stream().allMatch(f -> f.visibility().isEmpty()),
            entity.fields().toString());
        assertTrue(entity.methods().stream().allMatch(m -> m.visibility().isEmpty()),
            entity.methods().toString());
    }

    @Test
    void omitVisibility_neLaisseAucunEspaceDansLeDessin() {
        // Le symbole disparaît, et l'espace qui le suivait avec lui : sans quoi chaque ligne
        // commencerait par un blanc que rien ne justifie.
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

    // --- Le rangement d'un paquetage : @UMLPackage, et lui seul ---

    @UMLClass
    static class Rangee {}

    @UMLClass
    @UMLPackage(name = "Écrit à la main")
    static class DejaRangee {}

    private static final String PAQUETAGE = ReglesDuProjetTest.class.getPackageName();

    @Test
    void groupPackage_nExistePlus() {
        // Un paquetage est un nom, et un rectangle posé sur des classes nommées est un cas
        // particulier, pas un réglage de projet : c'est @UMLPackage qui le dit, sur l'élément.
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

    // --- Le fichier dans son ensemble ---

    @Test
    void fichierComplet_estLuSansErreur() {
        Regles lues = regles(
            "# Les réglages de tout le projet",
            "strategy public",
            "",
            "hide accessors",
            "hide object-methods",
            "hide with*, visit*, log, LOGGER",
            "omit return void",
            "omit visibility",
            "type ImmutableList -> List");

        assertFalse(lues.vides());
        assertEquals(UMLVisibility.PUBLIC, lues.strategie());
        assertTrue(lues.masque(Bruit.ACCESSORS));
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
