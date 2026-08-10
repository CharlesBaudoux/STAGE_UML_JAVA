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

// Les avertissements de fin de scan : quelles surprises sont signalées, et lesquelles ne le sont
// pas.
//
// La retenue compte autant que le signalement. Chacun de ces avertissements s'adresse à un
// utilisateur dont le diagramme est exact mais dit moins que ce qu'il croit avoir demandé : une
// boîte vide faute de stratégie, des signatures muettes faute de -parameters, un voisinage sans
// voisin. Un avertissement qui se déclencherait sur un choix délibéré — « strategy none » écrit,
// noms masqués par une règle — apprendrait à ne plus les lire.
class AvertissementsDeGenerationTest {

    private static Regles regles(String... lignes) {
        return Regles.lues(ImmutableList.copyOf(lignes), "umlg.rules");
    }

    // --- Les boîtes vides faute de stratégie ---

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
        // Le premier essai type : tout est annoté, rien n'est déclaré, les boîtes sortent des
        // coquilles vides et rien ne disait pourquoi.
        assertTrue(boiteVide(Muette.class, Regles.aucune()));
    }

    @Test
    void strategyNoneEcrit_faitTaireLeSignalement() {
        // La même boîte vide, mais choisie : une ligne écrite est une décision, pas un oubli.
        assertFalse(boiteVide(Muette.class, regles("strategy none")));
    }

    @Test
    void uneStrategieQuiRemplit_nAPlusRienASignaler() {
        assertFalse(boiteVide(Muette.class, regles("strategy public")));
    }

    @Test
    void uneClasseSansMembres_nEstPasSignalee() {
        // Une interface marqueur resterait vide sous toute stratégie : la signaler accuserait
        // la mauvaise cause.
        assertFalse(boiteVide(Marqueur.class, Regles.aucune()));
    }

    @Test
    void leSignalement_nommeLesBoites() {
        assertTrue(ProjectScanner.signalerBoitesVides(ImmutableList.of("Muette")));
        assertFalse(ProjectScanner.signalerBoitesVides(ImmutableList.of()));
    }

    // --- Les signatures muettes, marque d'une compilation sans -parameters ---

    private static UmlEntity entite(UmlMethod... methodes) {
        return UmlEntity.builder("com.exemple.Classe", "Classe")
            .methods(List.of(methodes)).build();
    }

    private static UmlMethod methodeAnonyme() {
        // Le constructeur de commodité fabrique des paramètres sans nom, exactement ce que rend
        // la réflexion sur un projet compilé sans l'option.
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
        // Un projet compilé avec -parameters a tous les noms ; en voir un seul prouve que
        // l'option est là, et que les absences éventuelles ont une autre cause.
        assertFalse(ProjectScanner.signalerParametresAnonymes(
            List.of(entite(methodeAnonyme()), entite(methodeNommee())), Regles.aucune()));
    }

    @Test
    void desNomsMasquesParLaRegle_neSontPasSignales() {
        // « omit parameter names » les a tus volontairement : rien à expliquer.
        assertFalse(ProjectScanner.signalerParametresAnonymes(
            List.of(entite(methodeAnonyme())), regles("omit parameter names")));
    }

    @Test
    void aucunParametreNullePart_neDitRien() {
        assertFalse(ProjectScanner.signalerParametresAnonymes(
            List.of(entite(new UmlMethod("lister", "int", "+", false, List.of()))),
            Regles.aucune()));
    }

    // --- Le voisinage sans voisin ---

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

    // --- Les règles restées lettre morte ---

    @Test
    void uneRegleTypeMalOrthographiee_estRelevee() {
        // Le no-op parfait d'hier : la règle ne s'appliquait jamais, le diagramme se dessinait,
        // et rien ne montrait la ligne morte. Le relevé la nomme telle qu'elle a été écrite.
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
        // Les deux natures de règles de nommage, chacune visant un nom que rien ne porte.
        Regles lues = regles("type ImutableList -> List", "hide get*", "hide LOGGR");
        lues.membreMasque("calculer");
        lues.membreMasque("log");

        // L'ordre du relevé suit celui des natures de règles : les types, puis les motifs.
        assertEquals(List.of("type ImutableList -> List", "hide get*", "hide LOGGR"),
            lues.reglesSansEffet());
    }

    @Test
    void leSignalementDesReglesMortes_neSurgitQueSiUneLigneEstMorte() {
        assertTrue(ProjectScanner.signalerReglesSansEffet(regles("hide LOGGR")));
        assertFalse(ProjectScanner.signalerReglesSansEffet(regles()));
    }

    // --- Les classes que le chargeur n'a pas su lire ---
    //
    // Elles ne font plus échouer le scan : une dépendance « provided » absente du classpath
    // transmis suffit à rendre un .class illisible, et il serait absurde qu'une classe dont le
    // diagramme ne veut pas empêche de le dessiner. Mais les taire serait pire — on ne peut pas
    // savoir si elles portaient @UMLClass, puisque l'annotation ne se lit que sur une classe
    // chargée. Le diagramme est donc peut-être incomplet, et cela se dit.

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
