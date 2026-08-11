package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemberExtractorTest {

    private static Optional<UmlField> champ(UmlEntity e, String nom) {
        return e.fields().stream().filter(f -> f.name().equals(nom)).findFirst();
    }

    private static List<UmlMethod> methodes(UmlEntity e, String nom) {
        return e.methods().stream().filter(m -> m.name().equals(nom)).toList();
    }

    private static Optional<UmlMethod> methode(UmlEntity e, String nom) {
        return methodes(e, nom).stream().findFirst();
    }

    private static List<String> types(UmlMethod methode) {
        return methode.parameters().stream().map(UmlParameter::type).toList();
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    enum Statut {
        ACTIF, SUSPENDU;

        private String libelle;

        public void basculer() {}
    }

    @Test
    void enum_nExposePasLeChampSyntheticVALUES() {
        UmlEntity entity = EntityExtractor.extract(Statut.class);

        assertTrue(champ(entity, "$VALUES").isEmpty(),
            "Le champ synthétique $VALUES ne doit pas apparaître : " + entity.fields());
    }

    @Test
    void enum_exposeSesConstantesEtSesChampsMetier() {
        UmlEntity entity = EntityExtractor.extract(Statut.class);

        assertTrue(entity.literals().contains("ACTIF"),
            "Les constantes d'enum sont attendues parmi les littéraux : " + entity.literals());
        assertTrue(champ(entity, "ACTIF").isEmpty(),
            "Une constante d'enum ne doit plus figurer parmi les attributs : " + entity.fields());
        assertTrue(champ(entity, "libelle").isPresent());
        assertTrue(methode(entity, "basculer").isPresent());
    }

    @Test
    void classeInstrumentee_neFuitPasSesChampsSynthetiques() {
        UmlEntity entity = EntityExtractor.extract(Statut.class);

        assertTrue(entity.fields().stream().noneMatch(f -> f.name().startsWith("$")),
            "Aucun champ synthétique ne doit apparaître : " + entity.fields());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    record Coordonnees(double latitude, double longitude) {
        public double distance(Coordonnees autre) {
            return 0;
        }
    }

    @Test
    void record_exposeSesComposantsCommeChamps() {
        UmlEntity entity = EntityExtractor.extract(Coordonnees.class);

        assertTrue(champ(entity, "latitude").isPresent());
        assertEquals("double", champ(entity, "longitude").orElseThrow().type());
        assertTrue(methode(entity, "distance").isPresent(), "La méthode métier doit rester.");
    }

    @Test
    void aucuneMethodeSynthetique_neRemonte() {
        UmlEntity entity = EntityExtractor.extract(Coordonnees.class);

        assertFalse(entity.methods().stream().anyMatch(m -> m.name().contains("$")),
            "Les méthodes synthétiques (lambdas, ponts) ne doivent pas apparaître : "
                + entity.methods());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    interface Paiement {
        void payer(int montant);
    }

    @Test
    void interface_exposeSesMethodesCommePubliques() {
        UmlEntity entity = EntityExtractor.extract(Paiement.class);

        UmlMethod payer = methode(entity, "payer").orElseThrow();
        assertEquals("+", payer.visibility(),
            "Une méthode d'interface est implicitement publique.");
        assertEquals(List.of("int"), types(payer));
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

    @Test
    void heritage_seulsLesMembresDeclaresSontExtraits() {
        UmlEntity entity = EntityExtractor.extract(Descendant.class);

        assertTrue(champ(entity, "champPropre").isPresent());
        assertTrue(champ(entity, "champHerite").isEmpty(),
            "Un champ hérité appartient à la classe mère, pas au descendant.");
        assertTrue(methode(entity, "methodePropre").isPresent());
        assertTrue(methode(entity, "methodeHeritee").isEmpty());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Surcharges {
        public void envoyer() {}

        public void envoyer(String message) {}

        public void envoyer(String message, int priorite) {}
    }

    @Test
    void surcharges_sontToutesConservees() {
        UmlEntity entity = EntityExtractor.extract(Surcharges.class);

        List<UmlMethod> envoyer = methodes(entity, "envoyer");
        assertEquals(3, envoyer.size(), "Les 3 surcharges doivent être présentes : " + envoyer);

        List<List<String>> signatures =
            envoyer.stream().map(MemberExtractorTest::types).toList();
        assertTrue(signatures.contains(List.of()), signatures.toString());
        assertTrue(signatures.contains(List.of("String")), signatures.toString());
        assertTrue(signatures.contains(List.of("String", "int")), signatures.toString());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class MethodeAvecParametres {
        public void calculer(int a, String b) {}
    }

    @Test
    void parametresDeMethode_sontExtraitsDansLOrdre() {
        UmlEntity entity = EntityExtractor.extract(MethodeAvecParametres.class);

        UmlMethod calculer = methode(entity, "calculer").orElseThrow();
        assertEquals(List.of("int", "String"), types(calculer));
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

    @Test
    void typesTableauxEtGeneriques_gardentLeursParametresDeType() {
        UmlEntity entity = EntityExtractor.extract(TypesVaries.class);

        assertEquals("int[]", champ(entity, "tableau").orElseThrow().type(),
            "Un tableau doit garder ses crochets.");
        assertEquals("List<String>", champ(entity, "liste").orElseThrow().type());

        UmlMethod lister = methode(entity, "lister").orElseThrow();
        assertEquals("List<String>", lister.returnType());
        assertEquals(List.of("int[]"), types(lister));
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class ClasseAvecStatique {
        public static final String CONSTANTE = "x";
        public static void methodeStatique() {}
    }

    @Test
    void membresStatiques_sontMarquesCommeTels() {
        UmlEntity entity = EntityExtractor.extract(ClasseAvecStatique.class);

        UmlField constante = champ(entity, "CONSTANTE").orElseThrow();
        assertTrue(constante.isStatic());

        UmlMethod methodeStatique = methode(entity, "methodeStatique").orElseThrow();
        assertTrue(methodeStatique.isStatic());
    }

    @Test
    void ordreDesMembres_estStableEntreDeuxExtractions() {
        UmlEntity a = EntityExtractor.extract(Surcharges.class);
        UmlEntity b = EntityExtractor.extract(Surcharges.class);

        assertEquals(a.methods(), b.methods());
        assertEquals(a.fields(), b.fields());
    }
}
