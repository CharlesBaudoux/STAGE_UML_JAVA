package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests sémantiques de l'extraction, complémentaires de {@link EntityExtractorTest} :
 * types Java particuliers (enum, record, interface, héritage), membres générés par le
 * compilateur, et limites du filtre anti-bruit basé sur les préfixes de noms.
 */
class EntityExtractorSemanticsTest {

    // --- Cobayes ---

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    enum Statut {
        ACTIF, SUSPENDU;

        private String libelle;

        public void basculer() {}
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    record Coordonnees(double latitude, double longitude) {
        public double distance(Coordonnees autre) {
            return 0;
        }
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    interface Paiement {
        void payer(int montant);
    }

    static class Ancetre {
        public String champHerite;

        public void methodeHeritee() {}
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Descendant extends Ancetre {
        private String champPropre;

        public void methodePropre() {}
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class AvecConstructeurs {
        public AvecConstructeurs() {}

        public AvecConstructeurs(String nom) {}

        public void agir() {}
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class PrefixesTrompeurs {
        public void setup() {}

        public void issue() {}

        public void getaway() {}

        public void isoler() {}

        public void calculer() {}
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

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Surcharges {
        public void envoyer() {}

        public void envoyer(String message) {}

        public void envoyer(String message, int priorite) {}
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class TypesVaries {
        public int[] tableau;
        public List<String> liste;
        public String texte;

        public List<String> lister(int[] entrees) {
            return List.of();
        }
    }

    // --- Utilitaires ---

    private static Optional<UmlField> champ(UmlEntity e, String nom) {
        return e.fields().stream().filter(f -> f.name().equals(nom)).findFirst();
    }

    private static List<UmlMethod> methodes(UmlEntity e, String nom) {
        return e.methods().stream().filter(m -> m.name().equals(nom)).toList();
    }

    private static Optional<UmlMethod> methode(UmlEntity e, String nom) {
        return methodes(e, nom).stream().findFirst();
    }

    // --- Membres générés par le compilateur ---

    @Test
    void enum_nExposePasLeChampSyntheticVALUES() {
        // javac ajoute à toute enum un champ synthétique « $VALUES » (le tableau interne
        // des constantes). Il n'a aucune valeur architecturale et son nom casse la lecture
        // du diagramme. Les méthodes sont filtrées via isSynthetic(), les champs doivent
        // l'être aussi.
        UmlEntity entity = EntityExtractor.extract(Statut.class);

        assertTrue(champ(entity, "$VALUES").isEmpty(),
            "Le champ synthétique $VALUES ne doit pas apparaître : " + entity.fields());
    }

    @Test
    void enum_exposeSesConstantesEtSesChampsMetier() {
        UmlEntity entity = EntityExtractor.extract(Statut.class);

        UmlField actif = champ(entity, "ACTIF").orElseThrow(
            () -> new AssertionError("Les constantes d'enum sont attendues : " + entity.fields()));
        assertTrue(actif.isStatic(), "Une constante d'enum est un champ static.");
        assertTrue(champ(entity, "libelle").isPresent());
        assertTrue(methode(entity, "basculer").isPresent());
    }

    @Test
    void classeInstrumentee_neFuitPasSesChampsSynthetiques() {
        // Généralisation du cas $VALUES : aucun champ synthétique, quelle qu'en soit
        // l'origine (javac, JaCoCo qui injecte $jacocoData, proxies...), ne doit remonter.
        UmlEntity entity = EntityExtractor.extract(Statut.class);

        assertTrue(entity.fields().stream().noneMatch(f -> f.name().startsWith("$")),
            "Aucun champ synthétique ne doit apparaître : " + entity.fields());
    }

    @Test
    void record_exposeSesComposantsCommeChamps() {
        UmlEntity entity = EntityExtractor.extract(Coordonnees.class);

        assertTrue(champ(entity, "latitude").isPresent());
        assertEquals("double", champ(entity, "longitude").orElseThrow().type());
        assertTrue(methode(entity, "distance").isPresent(), "La méthode métier doit rester.");
    }

    @Test
    void record_filtreSesAccesseursCanoniques() {
        // Les accesseurs d'un record s'appellent latitude()/longitude() : aucun préfixe, donc
        // invisibles pour un filtre basé sur get/set/is, alors qu'ils jouent exactement le
        // rôle d'un getter. Ils sont reconnus via getRecordComponents().
        UmlEntity entity = EntityExtractor.extract(Coordonnees.class);

        assertTrue(methode(entity, "latitude").isEmpty(),
            "L'accesseur canonique d'un record est du bruit : " + entity.methods());
        assertTrue(methode(entity, "longitude").isEmpty(), entity.methods().toString());
        assertEquals(List.of("distance"), entity.methods().stream().map(UmlMethod::name).toList(),
            "Seule la méthode métier doit rester.");
    }

    @Test
    void record_neFiltreQueLAccesseurExactPasSesSurcharges() {
        // valeur() est l'accesseur canonique ; valeur(int) est une surcharge métier qui porte
        // le même nom. Seule la signature exacte du composant (0 paramètre, type identique)
        // est du bruit.
        UmlEntity entity = EntityExtractor.extract(RecordAvecSurcharge.class);

        List<UmlMethod> valeur = methodes(entity, "valeur");

        assertEquals(1, valeur.size(),
            "L'accesseur sans paramètre doit partir, la surcharge rester : " + entity.methods());
        assertEquals(List.of("int"), valeur.get(0).parameterTypes());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    record RecordAvecSurcharge(int valeur) {
        public int valeur(int facteur) {
            return valeur * facteur;
        }
    }

    @Test
    void enum_filtreValuesEtValueOfGenereesParJavac() {
        // javac ajoute values() et valueOf(String) à toute enum. Elles ne sont pas marquées
        // synthétiques : isSynthetic() ne les attrape pas, et un filtre par préfixe non plus.
        UmlEntity entity = EntityExtractor.extract(Statut.class);

        assertTrue(methode(entity, "values").isEmpty(),
            "values() est générée par le compilateur : " + entity.methods());
        assertTrue(methode(entity, "valueOf").isEmpty(),
            "valueOf(String) est générée par le compilateur : " + entity.methods());
        assertEquals(List.of("basculer"), entity.methods().stream().map(UmlMethod::name).toList());
    }

    @Test
    void interface_exposeSesMethodesCommePubliques() {
        UmlEntity entity = EntityExtractor.extract(Paiement.class);

        UmlMethod payer = methode(entity, "payer").orElseThrow();
        assertEquals("+", payer.visibility(), "Une méthode d'interface est implicitement publique.");
        assertEquals(List.of("int"), payer.parameterTypes());
    }

    // --- Héritage ---

    @Test
    void heritage_seulsLesMembresDeclaresSontExtraits() {
        // getDeclaredFields/getDeclaredMethods ne remontent pas la hiérarchie : le diagramme
        // ne montre que ce que la classe déclare elle-même. C'est cohérent avec UML, où les
        // membres hérités appartiennent à la classe mère.
        UmlEntity entity = EntityExtractor.extract(Descendant.class);

        assertTrue(champ(entity, "champPropre").isPresent());
        assertTrue(champ(entity, "champHerite").isEmpty(),
            "Un champ hérité appartient à la classe mère, pas au descendant.");
        assertTrue(methode(entity, "methodePropre").isPresent());
        assertTrue(methode(entity, "methodeHeritee").isEmpty());
    }

    // --- Filtre anti-bruit ---

    @Test
    void constructeurs_nApparaissentJamais() {
        // La spec range les constructeurs dans le bruit.
        UmlEntity entity = EntityExtractor.extract(AvecConstructeurs.class);

        assertTrue(methodes(entity, "AvecConstructeurs").isEmpty());
        assertTrue(methodes(entity, "<init>").isEmpty());
        assertEquals(List.of("agir"), entity.methods().stream().map(UmlMethod::name).toList());
    }

    @Test
    void methodesHeriteesDObject_sontDuBruit() {
        UmlEntity entity = EntityExtractor.extract(BruitObject.class);

        assertEquals(List.of("agir"), entity.methods().stream().map(UmlMethod::name).toList(),
            "equals/hashCode/toString redéfinis restent du bruit : " + entity.methods());
    }

    @Test
    void methodesMetierCommencantParGetSetIs_neSontPasPrisesPourDesAccesseurs() {
        // Le danger d'un filtre par préfixe nu : il est SILENCIEUX. Ces cinq méthodes sont du
        // métier, mais « setup » commence par set, « issue »/« isoler » par is, « getaway »
        // par get. La convention JavaBean exige une majuscule après le préfixe, ce qui les
        // sauve toutes sans aucune annotation.
        UmlEntity entity = EntityExtractor.extract(PrefixesTrompeurs.class);

        List<String> visibles = entity.methods().stream().map(UmlMethod::name).toList();

        assertTrue(visibles.containsAll(List.of("setup", "issue", "getaway", "isoler", "calculer")),
            "Aucune de ces méthodes métier ne doit disparaître : " + visibles);
    }

    @Test
    void vraisAccesseursJavaBean_restentDuBruit() {
        // Le pendant du test précédent : l'assouplissement ne doit pas laisser passer les
        // accesseurs authentiques.
        UmlEntity entity = EntityExtractor.extract(AccesseursConformes.class);

        assertEquals(List.of("calculer"), entity.methods().stream().map(UmlMethod::name).toList(),
            "getNom/setNom/isActif sont de vrais accesseurs : " + entity.methods());
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
    void methodesRessemblantAUnAccesseurMaisDeMauvaiseSignature_restentVisibles() {
        // La convention JavaBean porte aussi sur l'arité et le type de retour, pas seulement
        // sur le nom : ces trois-là ont le bon préfixe et la majuscule, mais pas la forme.
        UmlEntity entity = EntityExtractor.extract(FaussesSignatures.class);

        List<String> visibles = entity.methods().stream().map(UmlMethod::name).toList();

        assertTrue(visibles.contains("getRapport"),
            "getRapport(int) prend un paramètre : ce n'est pas un getter. " + visibles);
        assertTrue(visibles.contains("setTout"),
            "setTout() ne prend aucun paramètre : ce n'est pas un setter. " + visibles);
        assertTrue(visibles.contains("isoler2"),
            "isoler2() ne renvoie pas un booléen : ce n'est pas un accesseur. " + visibles);
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
    void prefixeNu_estUneMethodeMetier() {
        // Choix délibéré : « get », « set » et « is » tout court ne sont pas des accesseurs
        // JavaBean (rien à accéder n'est nommé). Sur un Supplier, get() est même L'opération
        // métier de la classe. Elles restent donc affichées ; @UMLExclude est là pour ceux
        // qui pensent autrement.
        UmlEntity entity = EntityExtractor.extract(PrefixesNus.class);

        List<String> visibles = entity.methods().stream().map(UmlMethod::name).toList();

        assertTrue(visibles.containsAll(List.of("get", "set", "is")), visibles.toString());
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
    void accesseurBooleenRenvoyantLeWrapper_estAussiDuBruit() {
        // isActif() peut renvoyer Boolean plutôt que boolean (champ nullable) : c'est le même
        // accesseur.
        UmlEntity entity = EntityExtractor.extract(AccesseurWrapper.class);

        assertTrue(methode(entity, "isActif").isEmpty(), entity.methods().toString());
        assertEquals(List.of("agir"), entity.methods().stream().map(UmlMethod::name).toList());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class AccesseurWrapper {
        public Boolean isActif() {
            return true;
        }

        public void agir() {}
    }

    @Test
    void surchargesDesMethodesDObject_restentDuMetier() {
        // Seules les signatures exactes d'Object sont du bruit : equals(Client) est une
        // méthode métier que le développeur a écrite délibérément.
        UmlEntity entity = EntityExtractor.extract(SurchargeObject.class);

        List<String> visibles = entity.methods().stream().map(UmlMethod::name).toList();

        assertTrue(visibles.contains("equals"),
            "equals(SurchargeObject) est une surcharge, pas la méthode d'Object : " + entity.methods());
        assertEquals(1, methodes(entity, "equals").size(),
            "equals(Object) doit rester filtrée : " + entity.methods());
        assertTrue(visibles.contains("toString"),
            "toString(String) est une surcharge, pas la méthode d'Object : " + entity.methods());
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
    void umlInclude_recupereUneMethodeMetierVictimeDuFiltre() {
        // Corollaire du test précédent : la porte de sortie documentée par la spec.
        UmlEntity entity = EntityExtractor.extract(SetupRecupere.class);

        assertTrue(entity.methods().stream().anyMatch(m -> m.name().equals("setup")),
            "@UMLInclude doit rattraper un faux positif du filtre anti-bruit.");
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class SetupRecupere {
        @UMLInclude
        public void setup() {}
    }

    // --- Surcharges et types ---

    @Test
    void surcharges_sontToutesConservees() {
        // Trois méthodes de même nom : le diagramme doit les distinguer par leur signature,
        // aucune ne doit être avalée par une déduplication accidentelle.
        UmlEntity entity = EntityExtractor.extract(Surcharges.class);

        List<UmlMethod> envoyer = methodes(entity, "envoyer");
        assertEquals(3, envoyer.size(), "Les 3 surcharges doivent être présentes : " + envoyer);

        List<List<String>> signatures = envoyer.stream().map(UmlMethod::parameterTypes).toList();
        assertTrue(signatures.contains(List.of()), signatures.toString());
        assertTrue(signatures.contains(List.of("String")), signatures.toString());
        assertTrue(signatures.contains(List.of("String", "int")), signatures.toString());
    }

    @Test
    void typesTableauxEtGeneriques_sontRendusParLeurSimpleName() {
        UmlEntity entity = EntityExtractor.extract(TypesVaries.class);

        assertEquals("int[]", champ(entity, "tableau").orElseThrow().type(),
            "Un tableau doit garder ses crochets.");
        assertEquals("List", champ(entity, "liste").orElseThrow().type(),
            "getSimpleName() efface l'argument de type : List<String> devient List.");

        UmlMethod lister = methode(entity, "lister").orElseThrow();
        assertEquals("List", lister.returnType());
        assertEquals(List.of("int[]"), lister.parameterTypes());
    }

    // --- Invariants généraux ---

    @Test
    void ordreDesMembres_estStableEntreDeuxExtractions() {
        // getDeclaredMethods() ne garantit aucun ordre par contrat ; si l'ordre variait
        // d'une exécution à l'autre, le SVG produit changerait sans que le code change.
        UmlEntity a = EntityExtractor.extract(Surcharges.class);
        UmlEntity b = EntityExtractor.extract(Surcharges.class);

        assertEquals(a.methods(), b.methods());
        assertEquals(a.fields(), b.fields());
    }

    @Test
    void deuxExtractionsDeLaMemeClasse_sontEgales() {
        assertEquals(EntityExtractor.extract(Descendant.class), EntityExtractor.extract(Descendant.class),
            "UmlEntity est un record : deux extractions identiques doivent être égales.");
    }

    @Test
    void aucuneMethodeSynthetique_neRemonte() {
        UmlEntity entity = EntityExtractor.extract(Coordonnees.class);

        assertFalse(entity.methods().stream().anyMatch(m -> m.name().contains("$")),
            "Les méthodes synthétiques (lambdas, ponts) ne doivent pas apparaître : " + entity.methods());
    }
}
