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

// Ce que la réflexion sait des paramètres de type, et ce que le diagramme en fait.
//
// CE QUE CE FICHIER PROTÈGE
//
// La bibliothèque a longtemps affiché « List » là où le code écrit « List<Commande> », au motif
// que la compilation efface les paramètres de type. C'est vrai de ce qui vit à l'exécution, et
// faux des signatures déclarées : le compilateur les conserve dans l'attribut « Signature » du
// fichier .class, et la réflexion les rend par getGenericType().
//
// La différence n'est pas cosmétique. « Map<String, Short> » dit ce que la table associe ;
// « Map » ne dit rien. Et c'est ce qui permet de ne plus déclarer la cible d'une association que
// le type nomme déjà.
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

    // --- Les types tels qu'ils sont écrits ---

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
        // « ImmutableList » se renomme « List » où qu'il paraisse, y compris comme paramètre.
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

    // --- Le type de l'élément quand une multiplicité est écrite ---

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
        // « alias : Set [0..*] » se lirait « zéro ou plusieurs ensembles », ce qui est faux : il y
        // a un ensemble, contenant zéro ou plusieurs chaînes. UML veut ici le type de l'élément.
        UmlEntity entity = EntityExtractor.extract(AvecMultiplicites.class);

        assertEquals("String", champ(entity, "alias").type());
        assertEquals("0..*", champ(entity, "alias").multiplicity());
    }

    @Test
    void optional_estUneEnveloppeCommeUneAutre() {
        // Et son élément redevient primitif : Java interdit « Optional<int> », l'enveloppe
        // Integer est une contrainte du langage, pas un choix de l'auteur. « int [0..1] » est ce
        // que le diagramme doit dire — « Integer [0..1] » garderait la trace d'une contrainte
        // que le lecteur n'a pas à connaître.
        assertEquals("int", champ(EntityExtractor.extract(AvecMultiplicites.class), "ucp")
            .type());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class AvecEnveloppes {
        @UMLMultiplicity("1..*")
        private String[] noms;

        @UMLMultiplicity("0..*")
        private List<? extends CharSequence> textes;

        @UMLMultiplicity("0..*")
        private List<? super Commande> receptacles;

        @UMLMultiplicity("0..*")
        private List<String[]> lots;

        @UMLMultiplicity("1..*")
        private Integer[] compteurs;
    }

    @Test
    void unTableau_devantUneMultiplicite_perdSesCrochets() {
        // « noms : String[] [1..*] » se lit « un ou plusieurs tableaux de chaînes », alors que le
        // code n'en déclare qu'un. Un tableau est la façon la plus ancienne qu'a Java d'écrire
        // « plusieurs » : devant des crochets de multiplicité, il est l'enveloppe, comme un Set.
        assertEquals("String", champ(EntityExtractor.extract(AvecEnveloppes.class), "noms").type());
    }

    @Test
    void unJoker_devantUneMultiplicite_seReduitASaBorne() {
        // « ? extends CharSequence [0..*] » dit « zéro ou plusieurs choses qui étendent
        // CharSequence » ; la borne est tout ce que le joker apportait.
        UmlEntity entity = EntityExtractor.extract(AvecEnveloppes.class);

        assertEquals("CharSequence", champ(entity, "textes").type());
        // Sans borne haute, le joker n'a rien à dire de plus et s'écrit tel quel — « Object »
        // serait une affirmation que le code ne fait pas.
        assertEquals("?", champ(entity, "receptacles").type());
    }

    @Test
    void uneSeuleEnveloppe_estRetiree() {
        // « zéro ou plusieurs tableaux de chaînes » est exactement ce que le code déclare ici :
        // le tableau est à l'intérieur, et c'est la liste qui porte la multiplicité.
        UmlEntity entity = EntityExtractor.extract(AvecEnveloppes.class);

        assertEquals("String[]", champ(entity, "lots").type());
        // Et le déballage vers le primitif reste réservé aux conteneurs : « Integer[] » est un
        // choix de l'auteur, que « int[] » lui offrait déjà.
        assertEquals("Integer", champ(entity, "compteurs").type());
    }

    @Test
    void uneMapNEstPasDepliee() {
        // Deux paramètres : rien ne dirait lequel est la propriété. Le type reste écrit en
        // entier, ce qui demeure exact.
        assertEquals("Map<String, Integer>",
            champ(EntityExtractor.extract(AvecMultiplicites.class), "table").type());
    }

    @Test
    void laRegle_vautAussiPourLeRetourDUneMethode() {
        var commandes = EntityExtractor.extract(AvecMultiplicites.class).methods().stream()
            .filter(m -> m.name().equals("commandes")).findFirst().orElseThrow();

        assertEquals("Commande", commandes.returnType());
        assertEquals("0..*", commandes.multiplicity());
    }

    @Test
    void sansMultiplicite_leConteneurResteEcrit() {
        // Sans crochets, « List<Commande> » est le type de la propriété, et l'écrire en entier
        // est la seule chose juste.
        assertEquals("List<String>", champ(extraite(), "titres").type());
    }

    // --- La cible d'une association, déduite du type ---

    @UMLClass
    static class ClientDeduit {
        @UMLAssociation(targetMultiplicity = "0..*", notation = UMLNotation.ATTRIBUTE)
        private List<Commande> commandes;

        @UMLAssociation(notation = UMLNotation.ATTRIBUTE)
        private Commande courante;
    }

    @Test
    void laCibleDUneCollection_seLitDansSonParametre() {
        UmlEntity entity = EntityExtractor.extract(ClientDeduit.class);

        assertEquals("Commande", champ(entity, "commandes").type());
        assertEquals("0..*", champ(entity, "commandes").multiplicity());
    }

    @Test
    void laCibleDUnAttributSimple_estSonTypeMeme() {
        assertEquals("Commande",
            champ(EntityExtractor.extract(ClientDeduit.class), "courante").type());
    }

    @UMLClass
    static class LienDeduitEnTrait {
        @UMLAssociation(targetMultiplicity = "1..*")
        private List<Commande> commandes;
    }

    @Test
    void laDeduction_vautAussiPourUnLienDessineEnTrait() {
        ImmutableList<UmlAssociation> liens =
            EntityExtractor.extract(LienDeduitEnTrait.class).associations();

        assertEquals(1, liens.size());
        assertEquals(Commande.class.getName(), liens.get(0).targetQualifiedName());
    }

    // Les cobayes fautifs ne portent pas @UMLClass : le scan de target/test-classes les
    // rencontrerait et échouerait.
    static class CollectionBrute {
        @SuppressWarnings("rawtypes")
        @UMLAssociation(notation = UMLNotation.ATTRIBUTE)
        private List commandes;
    }

    @Test
    void uneCollectionSansParametre_exigeQueLaCibleSoitEcrite() {
        // C'est exactement le manque que target() est là pour combler : une liste brute ne dit
        // rien de ce qu'elle contient.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(CollectionBrute.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("does not say what it contains"), e.getMessage());
    }
}
