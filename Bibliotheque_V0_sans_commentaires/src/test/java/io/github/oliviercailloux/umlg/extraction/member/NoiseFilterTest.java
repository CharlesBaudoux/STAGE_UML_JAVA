package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// Le filtre anti-bruit, éprouvé des deux côtés : ce qu'il doit écarter, et surtout ce qu'il ne
// doit PAS écarter.
//
// La seconde moitié est la plus importante. Un filtre qui supprime trop est silencieux : la
// méthode manque au diagramme sans que rien ne le signale. D'où les cobayes aux noms trompeurs
// (setup, issue, getaway) et aux signatures presque conformes.
class NoiseFilterTest {

    private static List<String> nomsDesMethodes(UmlEntity entity) {
        return entity.methods().stream().map(UmlMethod::name).toList();
    }

    private static List<UmlMethod> methodes(UmlEntity e, String nom) {
        return e.methods().stream().filter(m -> m.name().equals(nom)).toList();
    }

    private static Optional<UmlMethod> methode(UmlEntity e, String nom) {
        return methodes(e, nom).stream().findFirst();
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class AccesseursSousStrategiePrivate {
        private String champPrive;

        public String getChampPrive() { return champPrive; }
        public void setChampPrive(String v) { champPrive = v; }
        public boolean isValide() { return true; }

        @Override
        public String toString() { return "AccesseursSousStrategiePrivate"; }

        public void methodeMetier() {}
    }

    @Test
    void strategiePrivate_filtreQuandMemeLeBruitDesMethodes() {
        UmlEntity entity = EntityExtractor.extract(AccesseursSousStrategiePrivate.class);

        assertTrue(methode(entity, "getChampPrive").isEmpty(),
            "Les getters sont du bruit, même en PRIVATE.");
        assertTrue(methode(entity, "setChampPrive").isEmpty(),
            "Les setters sont du bruit, même en PRIVATE.");
        assertTrue(methode(entity, "isValide").isEmpty(),
            "Les isXxx sont du bruit, même en PRIVATE.");
        assertTrue(methode(entity, "toString").isEmpty(),
            "toString() est du bruit hérité d'Object.");
        assertTrue(methode(entity, "methodeMetier").isPresent(),
            "Une vraie méthode métier doit rester visible.");
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class AccesseursConformes {
        public String getNom() {
            return "";
        }

        public void setNom(String nom) {}

        public boolean isActif() {
            return true;
        }

        public void calculer() {}
    }

    @Test
    void vraisAccesseursJavaBean_restentDuBruit() {
        UmlEntity entity = EntityExtractor.extract(AccesseursConformes.class);

        assertEquals(List.of("calculer"), nomsDesMethodes(entity),
            "getNom/setNom/isActif sont de vrais accesseurs : " + entity.methods());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class PrefixesTrompeurs {
        public void setup() {}

        public void issue() {}

        public void getaway() {}

        public void isoler() {}

        public void calculer() {}
    }

    @Test
    void methodesMetierCommencantParGetSetIs_neSontPasPrisesPourDesAccesseurs() {
        UmlEntity entity = EntityExtractor.extract(PrefixesTrompeurs.class);

        List<String> visibles = nomsDesMethodes(entity);

        assertTrue(visibles.containsAll(List.of("setup", "issue", "getaway", "isoler", "calculer")),
            "Aucune de ces méthodes métier ne doit disparaître : " + visibles);
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class FaussesSignatures {
        public String getRapport(int annee) {
            return "";
        }

        public void setTout() {}

        public String isoler2() {
            return "";
        }
    }

    @Test
    void methodesRessemblantAUnAccesseurMaisDeMauvaiseSignature_restentVisibles() {
        UmlEntity entity = EntityExtractor.extract(FaussesSignatures.class);

        List<String> visibles = nomsDesMethodes(entity);

        assertTrue(visibles.contains("getRapport"),
            "getRapport(int) prend un paramètre : ce n'est pas un getter. " + visibles);
        assertTrue(visibles.contains("setTout"),
            "setTout() ne prend aucun paramètre : ce n'est pas un setter. " + visibles);
        assertTrue(visibles.contains("isoler2"),
            "isoler2() ne renvoie pas un booléen : ce n'est pas un accesseur. " + visibles);
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class PrefixesNus {
        public String get() {
            return "";
        }

        public void set(String valeur) {}

        public boolean is() {
            return true;
        }
    }

    @Test
    void prefixeNu_estUneMethodeMetier() {
        UmlEntity entity = EntityExtractor.extract(PrefixesNus.class);

        List<String> visibles = nomsDesMethodes(entity);

        assertTrue(visibles.containsAll(List.of("get", "set", "is")), visibles.toString());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class AccesseurWrapper {
        public Boolean isActif() {
            return true;
        }

        public void agir() {}
    }

    @Test
    void accesseurBooleenRenvoyantLeWrapper_estAussiDuBruit() {
        UmlEntity entity = EntityExtractor.extract(AccesseurWrapper.class);

        assertTrue(methode(entity, "isActif").isEmpty(), entity.methods().toString());
        assertEquals(List.of("agir"), nomsDesMethodes(entity));
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class BruitObject {
        @Override
        public boolean equals(Object o) {
            return super.equals(o);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public String toString() {
            return "";
        }

        public void agir() {}
    }

    @Test
    void methodesHeriteesDObject_sontDuBruit() {
        UmlEntity entity = EntityExtractor.extract(BruitObject.class);

        assertEquals(List.of("agir"), nomsDesMethodes(entity),
            "equals/hashCode/toString redéfinis restent du bruit : " + entity.methods());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class SurchargeObject {
        public boolean equals(SurchargeObject autre) {
            return true;
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        public String toString(String format) {
            return format;
        }
    }

    @Test
    void surchargesDesMethodesDObject_restentDuMetier() {
        UmlEntity entity = EntityExtractor.extract(SurchargeObject.class);

        List<String> visibles = nomsDesMethodes(entity);

        assertTrue(visibles.contains("equals"),
            "equals(SurchargeObject) est une surcharge, pas la méthode d'Object : "
                + entity.methods());
        assertEquals(1, methodes(entity, "equals").size(),
            "equals(Object) doit rester filtrée : " + entity.methods());
        assertTrue(visibles.contains("toString"),
            "toString(String) est une surcharge, pas la méthode d'Object : " + entity.methods());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class AvecConstructeurs {
        public AvecConstructeurs() {}

        public AvecConstructeurs(String nom) {}

        public void agir() {}
    }

    @Test
    void constructeurs_nApparaissentJamais() {
        UmlEntity entity = EntityExtractor.extract(AvecConstructeurs.class);

        assertTrue(methodes(entity, "AvecConstructeurs").isEmpty());
        assertTrue(methodes(entity, "<init>").isEmpty());
        assertEquals(List.of("agir"), nomsDesMethodes(entity));
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    record Coordonnees(double latitude, double longitude) {
        public double distance(Coordonnees autre) {
            return 0;
        }
    }

    @Test
    void record_filtreSesAccesseursCanoniques() {
        UmlEntity entity = EntityExtractor.extract(Coordonnees.class);

        assertTrue(methode(entity, "latitude").isEmpty(),
            "L'accesseur canonique d'un record est du bruit : " + entity.methods());
        assertTrue(methode(entity, "longitude").isEmpty(), entity.methods().toString());
        assertEquals(List.of("distance"), nomsDesMethodes(entity),
            "Seule la méthode métier doit rester.");
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    record RecordAvecSurcharge(int valeur) {
        public int valeur(int facteur) {
            return valeur * facteur;
        }
    }

    @Test
    void record_neFiltreQueLAccesseurExactPasSesSurcharges() {
        UmlEntity entity = EntityExtractor.extract(RecordAvecSurcharge.class);

        List<UmlMethod> valeur = methodes(entity, "valeur");

        assertEquals(1, valeur.size(),
            "L'accesseur sans paramètre doit partir, la surcharge rester : " + entity.methods());
        assertEquals(List.of("int"), valeur.get(0).parameterTypes());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    enum Statut {
        ACTIF, SUSPENDU;

        private String libelle;

        public void basculer() {}
    }

    @Test
    void enum_filtreValuesEtValueOfGenereesParJavac() {
        UmlEntity entity = EntityExtractor.extract(Statut.class);

        assertTrue(methode(entity, "values").isEmpty(),
            "values() est générée par le compilateur : " + entity.methods());
        assertTrue(methode(entity, "valueOf").isEmpty(),
            "valueOf(String) est générée par le compilateur : " + entity.methods());
        assertEquals(List.of("basculer"), nomsDesMethodes(entity));
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class IncludeSurGetter {
        private String secretCritique;

        @UMLInclude
        private String getSecretCritique() { return secretCritique; }

        private void methodePriveeIgnoree() {}
    }

    @Test
    void umlInclude_forceUnGetterMasqueParLeFiltreAntiBruit() {
        UmlEntity entity = EntityExtractor.extract(IncludeSurGetter.class);

        assertTrue(methode(entity, "getSecretCritique").isPresent(),
            "@UMLInclude doit pouvoir forcer l'affichage d'un getter normalement filtré comme "
                + "bruit.");
        assertTrue(methode(entity, "methodePriveeIgnoree").isEmpty());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class SetupRecupere {
        @UMLInclude
        public void setup() {}
    }

    @Test
    void umlInclude_recupereUneMethodeMetierVictimeDuFiltre() {
        UmlEntity entity = EntityExtractor.extract(SetupRecupere.class);

        assertTrue(nomsDesMethodes(entity).contains("setup"),
            "@UMLInclude doit rattraper un faux positif du filtre anti-bruit.");
    }
}
