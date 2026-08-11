package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypesGeneriquesTest {

    @UMLClass
    static class Commande {}

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Bibliotheque {
        private List<String> titres;
        private Map<String, Integer> comptes;
        private Set<? extends Commande> encours;
        private List<String>[] pages;
        private String simple;

        public Map<String, List<Commande>> parClient() {
            return Map.of();
        }

        public void ajouter(List<Commande> lot) {}
    }

    private static UmlField champ(UmlEntity entity, String nom) {
        return entity.fields().stream().filter(f -> f.name().equals(nom)).findFirst()
            .orElseThrow();
    }

    private static UmlEntity extraite() {
        return EntityExtractor.extract(Bibliotheque.class);
    }

    @Test
    void unParametreDeType_estAffiche() {
        assertEquals("List<String>", champ(extraite(), "titres").type());
    }

    @Test
    void deuxParametres_sontAffichesTousLesDeux() {
        assertEquals("Map<String, Integer>", champ(extraite(), "comptes").type());
    }

    @Test
    void unJoker_gardeSaBorne() {
        assertEquals("Set<? extends Commande>", champ(extraite(), "encours").type());
    }

    @Test
    void unTableauDeGeneriques_gardeSesCrochets() {
        assertEquals("List<String>[]", champ(extraite(), "pages").type());
    }

    @Test
    void unTypeOrdinaire_estInchange() {
        assertEquals("String", champ(extraite(), "simple").type());
    }

    @Test
    void lesParametres_valentAussiPourLesMethodes() {
        var parClient = extraite().methods().stream()
            .filter(m -> m.name().equals("parClient")).findFirst().orElseThrow();
        var ajouter = extraite().methods().stream()
            .filter(m -> m.name().equals("ajouter")).findFirst().orElseThrow();

        assertEquals("Map<String, List<Commande>>", parClient.returnType());
        assertEquals("List<Commande>", ajouter.parameters().get(0).type());
    }

    @Test
    void lesReglesDeRenommage_entrentDansLesChevrons() {
        Regles regles = Regles.lues("""
            { "types": { "ImmutableList": "List" } }
            """,
            "les règles du test");

        assertEquals("List<String>",
            typeDuChamp(regles, "titres"));
    }

    private static String typeDuChamp(Regles regles, String nom) {
        return EntityExtractor.extract(Bibliotheque.class, regles).fields().stream()
            .filter(f -> f.name().equals(nom)).findFirst().orElseThrow().type();
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class AvecMultiplicites {
        @UMLMultiplicity("0..*")
        private Set<String> alias;

        @UMLMultiplicity("0..1")
        private Optional<Integer> ucp;

        @UMLMultiplicity("0..*")
        private Map<String, Integer> table;

        @UMLMultiplicity("0..*")
        public List<Commande> commandes() {
            return List.of();
        }
    }

    @Test
    void avecUneMultiplicite_cEstLeTypeDeLElementQuiSEcrit() {
        UmlEntity entity = EntityExtractor.extract(AvecMultiplicites.class);

        assertEquals("String", champ(entity, "alias").type());
        assertEquals("0..*", champ(entity, "alias").cardinality());
    }

    @Test
    void optional_estUneEnveloppeCommeUneAutre() {
        assertEquals("int", champ(EntityExtractor.extract(AvecMultiplicites.class), "ucp")
            .type());
    }

    @Test
    void uneMapNEstPasDepliee() {
        assertEquals("Map<String, Integer>",
            champ(EntityExtractor.extract(AvecMultiplicites.class), "table").type());
    }

    @Test
    void laRegle_vautAussiPourLeRetourDUneMethode() {
        var commandes = EntityExtractor.extract(AvecMultiplicites.class).methods().stream()
            .filter(m -> m.name().equals("commandes")).findFirst().orElseThrow();

        assertEquals("Commande", commandes.returnType());
        assertEquals("0..*", commandes.cardinality());
    }

    @Test
    void sansMultiplicite_leConteneurResteEcrit() {
        assertEquals("List<String>", champ(extraite(), "titres").type());
    }

    @UMLClass
    static class ClientDeduit {
        @UMLAssociation(targetCardinality = "0..*", notation = UMLNotation.ATTRIBUTE)
        private List<Commande> commandes;

        @UMLAssociation(notation = UMLNotation.ATTRIBUTE)
        private Commande courante;
    }

    @Test
    void laCibleDUneCollection_seLitDansSonParametre() {
        UmlEntity entity = EntityExtractor.extract(ClientDeduit.class);

        assertEquals("Commande", champ(entity, "commandes").type());
        assertEquals("0..*", champ(entity, "commandes").cardinality());
    }

    @Test
    void laCibleDUnAttributSimple_estSonTypeMeme() {
        assertEquals("Commande",
            champ(EntityExtractor.extract(ClientDeduit.class), "courante").type());
    }

    @UMLClass
    static class LienDeduitEnTrait {
        @UMLAssociation(targetCardinality = "1..*")
        private List<Commande> commandes;
    }

    @Test
    void laDeduction_vautAussiPourUnLienDessineEnTrait() {
        ImmutableList<UmlAssociation> liens =
            EntityExtractor.extract(LienDeduitEnTrait.class).associations();

        assertEquals(1, liens.size());
        assertEquals(Commande.class.getName(), liens.get(0).targetQualifiedName());
    }

    static class CollectionBrute {
        @SuppressWarnings("rawtypes")
        @UMLAssociation(notation = UMLNotation.ATTRIBUTE)
        private List commandes;
    }

    @Test
    void uneCollectionSansParametre_exigeQueLaCibleSoitEcrite() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(CollectionBrute.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("does not say what it contains"), e.getMessage());
    }
}
