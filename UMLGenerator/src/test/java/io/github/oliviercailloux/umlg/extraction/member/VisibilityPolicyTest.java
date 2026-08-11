package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// Le tableau complet des cinq stratégies, et la préséance des deux annotations de dérogation.
//
// Chaque stratégie a sa classe cobaye, portant les quatre visibilités Java : le test peut alors
// vérifier d'un coup ce qui entre et ce qui reste dehors. Les tests passent par
// EntityExtractor.extract plutôt que par VisibilityPolicy directement — c'est le comportement
// visible de bout en bout qui est fixé ici, pas le détail de l'implémentation.
class VisibilityPolicyTest {

    // Sans strategy : le défaut NONE.
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
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class DerogationInclude {
        @UMLInclude
        private String secretCritique;

        private String secretIgnorable;
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

    // Deux aides de lecture : « ce membre est-il dans la boîte ? ». L'Optional rend l'assertion
    // lisible dans les deux sens — isPresent() pour « doit y être », isEmpty() pour « ne doit
    // pas y être ».
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

        assertTrue(entity.fields().isEmpty(),
            "Sans strategy, aucun attribut ne doit être extrait.");
        assertTrue(entity.methods().isEmpty(),
            "Sans strategy, aucune méthode ne doit être extraite.");
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
    // En Java, un membre protected est visible depuis le paquetage ; le cahier des charges en
    // décide pourtant autrement, et c'est ce test qui fige ce choix.

    @Test
    void strategiePackage_gardePublicEtPackagePrivateMaisPasProtected() {
        UmlEntity entity = EntityExtractor.extract(StrategiePackage.class);

        assertTrue(field(entity, "champPublic").isPresent());
        assertTrue(field(entity, "champPackage").isPresent());
        assertTrue(field(entity, "champProtected").isEmpty(),
            "Selon la spec, PACKAGE exclut explicitement le protected.");
        assertTrue(field(entity, "champPrive").isEmpty());
    }

    // --- Stratégie PRIVATE : tout ---

    @Test
    void strategiePrivate_gardeTousLesChamps() {
        UmlEntity entity = EntityExtractor.extract(StrategiePrivate.class);

        assertTrue(field(entity, "champPublic").isPresent());
        assertTrue(field(entity, "champProtected").isPresent());
        assertTrue(field(entity, "champPackage").isPresent());
        assertTrue(field(entity, "champPrive").isPresent());
    }

    // --- @UMLInclude : dérogation positive ---

    @Test
    void umlInclude_forcePrivateChampMalgreStrategiePublic() {
        UmlEntity entity = EntityExtractor.extract(DerogationInclude.class);

        assertTrue(field(entity, "secretCritique").isPresent(),
            "@UMLInclude doit forcer l'affichage même si le champ est private.");
        assertTrue(field(entity, "secretIgnorable").isEmpty(),
            "Sans @UMLInclude, un champ private doit rester masqué sous strategy PUBLIC.");
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
}
