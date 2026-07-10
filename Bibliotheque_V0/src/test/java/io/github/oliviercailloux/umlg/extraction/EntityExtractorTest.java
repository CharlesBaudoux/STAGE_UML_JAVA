package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests du moteur de décision de EntityExtractor : la partie "métier" de la
 * bibliothèque (stratégies de visibilité, filtre anti-bruit, dérogations
 * @UMLInclude/@UMLExclude). On vérifie le CONTENU produit, pas seulement
 * l'absence d'exception.
 */
class EntityExtractorTest {

    // --- Cobayes : un par scénario, pour isoler chaque règle ---

    @UMLClass
    static class StrategieAbsente {
        public String champPublic;
        public void methodeMetierPublique() {}
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class StrategiePublic {
        public String champPublic;
        protected String champProtected;
        String champPackage;
        private String champPrive;

        public void methodePublique() {}
        protected void methodeProtected() {}
        void methodePackage() {}
        private void methodePrivee() {}
    }

    @UMLClass(strategy = UMLVisibility.PROTECTED)
    static class StrategieProtected {
        public String champPublic;
        protected String champProtected;
        String champPackage;
        private String champPrive;
    }

    @UMLClass(strategy = UMLVisibility.PACKAGE)
    static class StrategiePackage {
        public String champPublic;
        protected String champProtected;
        String champPackage;
        private String champPrive;
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class StrategiePrivate {
        public String champPublic;
        protected String champProtected;
        String champPackage;
        private String champPrive;

        public String getChampPrive() { return champPrive; }
        public void setChampPrive(String v) { champPrive = v; }
        public boolean isValide() { return true; }

        @Override
        public String toString() { return "StrategiePrivate"; }

        public void methodeMetier() {}
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class DerogationInclude {
        @UMLInclude
        private String secretCritique;

        private String secretIgnorable;

        @UMLInclude
        private String getSecretCritique() { return secretCritique; }

        private void methodePriveeIgnoree() {}
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class DerogationExclude {
        @UMLExclude
        private String champTechnique;

        private String champMetier;

        @UMLExclude
        public void methodeMetierMasquee() {}

        public void methodeMetierVisible() {}
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class ExcludeGagneSurInclude {
        // Cas limite : les deux annotations sont présentes -> @UMLExclude doit gagner.
        @UMLInclude
        @UMLExclude
        private String champAmbigu;
    }

    @UMLClass(name = "NomPersonnalise", stereotype = "Entity", color = "AABBCC")
    static class ClasseSansMembres {}

    @UMLClass(color = "#ABC")
    static class CouleurHexCourte {}

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class ClasseAvecStatique {
        public static final String CONSTANTE = "x";
        public static void methodeStatique() {}
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class MethodeAvecParametres {
        public void calculer(int a, String b) {}
    }

    static class ClasseNonAnnotee {}

    // --- Utilitaires de recherche ---

    private static Optional<UmlField> field(UmlEntity entity, String name) {
        return entity.fields().stream().filter(f -> f.name().equals(name)).findFirst();
    }

    private static Optional<UmlMethod> method(UmlEntity entity, String name) {
        return entity.methods().stream().filter(m -> m.name().equals(name)).findFirst();
    }

    // --- Stratégie NONE (par défaut) : coquille vide ---

    @Test
    void aucuneStrategie_produitUneCoquilleVide() {
        UmlEntity entity = EntityExtractor.extract(StrategieAbsente.class);

        assertTrue(entity.fields().isEmpty(), "Sans strategy, aucun attribut ne doit être extrait.");
        assertTrue(entity.methods().isEmpty(), "Sans strategy, aucune méthode ne doit être extraite.");
    }

    // --- Stratégie PUBLIC ---

    @Test
    void strategiePublic_neGardeQueLesMembresPublics() {
        UmlEntity entity = EntityExtractor.extract(StrategiePublic.class);

        assertTrue(field(entity, "champPublic").isPresent());
        assertTrue(field(entity, "champProtected").isEmpty());
        assertTrue(field(entity, "champPackage").isEmpty());
        assertTrue(field(entity, "champPrive").isEmpty());

        assertTrue(method(entity, "methodePublique").isPresent());
        assertTrue(method(entity, "methodeProtected").isEmpty());
        assertTrue(method(entity, "methodePackage").isEmpty());
        assertTrue(method(entity, "methodePrivee").isEmpty());
    }

    // --- Stratégie PROTECTED ---

    @Test
    void strategieProtected_gardePublicEtProtected() {
        UmlEntity entity = EntityExtractor.extract(StrategieProtected.class);

        assertTrue(field(entity, "champPublic").isPresent());
        assertTrue(field(entity, "champProtected").isPresent());
        assertTrue(field(entity, "champPackage").isEmpty(),
            "PROTECTED ne doit pas inclure le package-private.");
        assertTrue(field(entity, "champPrive").isEmpty());
    }

    // --- Stratégie PACKAGE : le point le plus piégeux du cahier des charges ---

    @Test
    void strategiePackage_gardePublicEtPackagePrivateMaisPasProtected() {
        UmlEntity entity = EntityExtractor.extract(StrategiePackage.class);

        assertTrue(field(entity, "champPublic").isPresent());
        assertTrue(field(entity, "champPackage").isPresent());
        assertTrue(field(entity, "champProtected").isEmpty(),
            "Selon la spec, PACKAGE exclut explicitement le protected.");
        assertTrue(field(entity, "champPrive").isEmpty());
    }

    // --- Stratégie PRIVATE : tout, sauf le bruit ---

    @Test
    void strategiePrivate_gardeTousLesChamps() {
        UmlEntity entity = EntityExtractor.extract(StrategiePrivate.class);

        assertTrue(field(entity, "champPublic").isPresent());
        assertTrue(field(entity, "champProtected").isPresent());
        assertTrue(field(entity, "champPackage").isPresent());
        assertTrue(field(entity, "champPrive").isPresent());
    }

    @Test
    void strategiePrivate_filtreQuandMemeLeBruitDesMethodes() {
        UmlEntity entity = EntityExtractor.extract(StrategiePrivate.class);

        assertTrue(method(entity, "getChampPrive").isEmpty(), "Les getters sont du bruit, même en PRIVATE.");
        assertTrue(method(entity, "setChampPrive").isEmpty(), "Les setters sont du bruit, même en PRIVATE.");
        assertTrue(method(entity, "isValide").isEmpty(), "Les isXxx sont du bruit, même en PRIVATE.");
        assertTrue(method(entity, "toString").isEmpty(), "toString() est du bruit hérité d'Object.");
        assertTrue(method(entity, "methodeMetier").isPresent(),
            "Une vraie méthode métier doit rester visible.");
    }

    // --- @UMLInclude : dérogation positive, y compris sur le bruit (bug corrigé) ---

    @Test
    void umlInclude_forcePrivateChampMalgreStrategiePublic() {
        UmlEntity entity = EntityExtractor.extract(DerogationInclude.class);

        assertTrue(field(entity, "secretCritique").isPresent(),
            "@UMLInclude doit forcer l'affichage même si le champ est private.");
        assertTrue(field(entity, "secretIgnorable").isEmpty(),
            "Sans @UMLInclude, un champ private doit rester masqué sous strategy PUBLIC.");
    }

    @Test
    void umlInclude_forceUnGetterMasqueParLeFiltreAntiBruit() {
        // Ancien bug : le filtre anti-bruit s'appliquait avant la vérification de @UMLInclude,
        // rendant impossible de forcer l'affichage d'un getter comme documenté dans annotation.tex
        // ("Il reste possible de forcer l'affichage d'un élément masqué avec @UMLInclude").
        UmlEntity entity = EntityExtractor.extract(DerogationInclude.class);

        assertTrue(method(entity, "getSecretCritique").isPresent(),
            "@UMLInclude doit pouvoir forcer l'affichage d'un getter normalement filtré comme bruit.");
        assertTrue(method(entity, "methodePriveeIgnoree").isEmpty());
    }

    // --- @UMLExclude : dérogation négative ---

    @Test
    void umlExclude_masqueUnChampAutoriseParLaStrategie() {
        UmlEntity entity = EntityExtractor.extract(DerogationExclude.class);

        assertTrue(field(entity, "champTechnique").isEmpty(),
            "@UMLExclude doit masquer le champ même si strategy PRIVATE l'autoriserait.");
        assertTrue(field(entity, "champMetier").isPresent());
    }

    @Test
    void umlExclude_masqueUneMethodeMetierAutoriseeParLaStrategie() {
        UmlEntity entity = EntityExtractor.extract(DerogationExclude.class);

        assertTrue(method(entity, "methodeMetierMasquee").isEmpty());
        assertTrue(method(entity, "methodeMetierVisible").isPresent());
    }

    @Test
    void umlExclude_gagneToujoursSurUmlInclude() {
        UmlEntity entity = EntityExtractor.extract(ExcludeGagneSurInclude.class);

        assertTrue(field(entity, "champAmbigu").isEmpty(),
            "En cas de conflit, @UMLExclude doit l'emporter sur @UMLInclude.");
    }

    // --- Métadonnées de la classe ---

    @Test
    void nomStereotypeEtCouleur_sontRepercutesSurLEntite() {
        UmlEntity entity = EntityExtractor.extract(ClasseSansMembres.class);

        assertEquals("NomPersonnalise", entity.name());
        assertEquals("Entity", entity.stereotype());
        assertEquals("AABBCC", entity.color());
        assertTrue(entity.fields().isEmpty());
        assertTrue(entity.methods().isEmpty());
    }

    @Test
    void nomParDefaut_estLeSimpleNameQuandNonSurcharge() {
        UmlEntity entity = EntityExtractor.extract(StrategieAbsente.class);

        assertEquals("StrategieAbsente", entity.name());
    }

    // --- Sécurisation des paramètres de @UMLClass (name, color, stereotype) ---

    @Test
    void couleurInvalide_estRejetee() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityExtractor.validateColor("pasunecouleur", getClass()),
            "Une couleur qui n'est pas un code hexadécimal doit être rejetée.");
    }

    @Test
    void couleurHexCourte_estAcceptee() {
        UmlEntity entity = EntityExtractor.extract(CouleurHexCourte.class);
        assertEquals("#ABC", entity.color(), "Le format hexadécimal à 3 chiffres doit être accepté.");
    }

    @Test
    void nomAvecGuillemet_estRejete() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityExtractor.validateName("Nom\"Injecte", getClass()),
            "Un nom contenant un guillemet (risque de casser la syntaxe PlantUML) doit être rejeté.");
    }

    @Test
    void nomBlanc_estRejete() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityExtractor.validateName("   ", getClass()),
            "Un nom composé uniquement d'espaces doit être rejeté.");
    }

    @Test
    void stereotypeAvecChevrons_estRejete() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityExtractor.validateStereotype("<<Hack>>", getClass()),
            "Un stéréotype contenant des chevrons (risque de casser la syntaxe PlantUML) doit être rejeté.");
    }

    // --- Symboles de visibilité et modificateur static ---

    @Test
    void membresStatiques_sontMarquesCommeTels() {
        UmlEntity entity = EntityExtractor.extract(ClasseAvecStatique.class);

        UmlField constante = field(entity, "CONSTANTE").orElseThrow();
        assertTrue(constante.isStatic());

        UmlMethod methodeStatique = method(entity, "methodeStatique").orElseThrow();
        assertTrue(methodeStatique.isStatic());
    }

    @Test
    void parametresDeMethode_sontExtraitsDansLOrdre() {
        UmlEntity entity = EntityExtractor.extract(MethodeAvecParametres.class);

        UmlMethod calculer = method(entity, "calculer").orElseThrow();
        assertEquals(java.util.List.of("int", "String"), calculer.parameterTypes());
    }

    // --- Garde-fous ---

    @Test
    void extractionEchoueSiLaClasseNEstPasAnnotee() {
        assertThrows(IllegalArgumentException.class, () -> EntityExtractor.extract(ClasseNonAnnotee.class));
    }

    @Test
    void extractionEchoueSurNull() {
        assertThrows(NullPointerException.class, () -> EntityExtractor.extract(null));
    }

    @Test
    void champsSontListesImmuables() {
        UmlEntity entity = EntityExtractor.extract(StrategiePublic.class);

        assertThrows(UnsupportedOperationException.class, () -> entity.fields().add(null));
    }

    @Test
    void constructeurRejetteLesArgumentsNuls() {
        assertThrows(NullPointerException.class, () -> new UmlEntity(null, "", "", UMLVisibility.NONE,
            java.util.List.of(), java.util.List.of()));
    }
}
