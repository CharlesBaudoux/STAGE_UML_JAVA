package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

// Deux assouplissements qui réduisent le coût d'annotation, mesurés sur un projet réel :
// onze lignes d'annotations pour trois boîtes triviales de records.
//
// Un composant de record est la définition même du record : il paraît quelle que soit la
// stratégie, NONE comprise — cette stratégie est faite pour choisir parmi des membres
// ordinaires, pas pour taire une définition. Et un @UMLExclude posé sur la déclaration d'une
// interface vaut pour chacune de ses réalisations : le membre s'exclut là où il est déclaré,
// une fois.
class RecordsEtExclusionsHeriteesTest {

    // Sans @UMLClass : les tests d'intégration scannent tout target/test-classes, et
    // MemberExtractor n'en a pas besoin.
    record Mnemonic(String keysymMnemonic) implements Entree {
        @Override
        public String asString() {
            return keysymMnemonic;
        }
    }

    @Test
    void unComposantDeRecord_paraitMemeSousStrategyNone() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Mnemonic.class, UMLVisibility.NONE);

        assertEquals(1, champs.size(), champs.toString());
        assertEquals("keysymMnemonic", champs.get(0).name());
        assertEquals("String", champs.get(0).type());
    }

    // --- L'inclusion écrite sur un composant, et le ricochet de Java ---

    record Renomme(@UMLInclude(name = "mnemonic") String keysymMnemonic) {}

    @Test
    void uneInclusionSurUnComposant_renommeLAttribut() {
        // Le besoin qui justifie le paramètre « name » : le diagramme de référence nomme les
        // choses plus longuement, ou autrement, que le code.
        List<UmlField> champs =
            MemberExtractor.extractFields(Renomme.class, UMLVisibility.NONE);

        assertEquals(1, champs.size(), champs.toString());
        assertEquals("mnemonic", champs.get(0).name());
    }

    @Test
    void uneInclusionSurUnComposant_nAjoutePasLAccesseur() {
        // Le compilateur recopie l'annotation sur le champ ET sur l'accesseur : @UMLInclude vise
        // FIELD et METHOD. L'accesseur, ainsi inclus, paraissait en plus du composant — la boîte
        // montrait « mnemonic : String » et « mnemonic() : String » pour une seule intention,
        // sans que rien ne le signale.
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Renomme.class, UMLVisibility.NONE);

        assertTrue(methodes.isEmpty(), "L'accesseur ne doit pas doubler le composant : "
            + methodes);
    }

    record AccesseurEcrit(String composant) {
        @UMLInclude(name = "lu")
        public String composant() {
            return composant;
        }
    }

    @Test
    void uneInclusionSurUnAccesseurEcritALaMain_estRespectee() {
        // Le miroir du test précédent, et ce qui rend l'écart sûr : ici rien ne s'est propagé —
        // le champ ne porte pas l'annotation —, l'auteur a bien visé la méthode, et elle reste.
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(AccesseurEcrit.class, UMLVisibility.NONE);

        assertEquals(1, methodes.size(), methodes.toString());
        assertEquals("lu", methodes.get(0).name());
    }

    record Cache(String visible, @UMLExclude String tu) {}

    @Test
    void unComposantExclu_disparait_leVetoIndividuelGardeLeDernierMot() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Cache.class, UMLVisibility.NONE);

        assertEquals(1, champs.size(), champs.toString());
        assertEquals("visible", champs.get(0).name());
    }

    record AvecConstante(String composant) {
        static final int SEUIL = 3;
    }

    @Test
    void unChampAjouteAuRecord_suitLaStrategieOrdinaire() {
        // Seuls les composants sont la définition du record ; un champ statique ajouté reste un
        // membre comme un autre, que NONE tait.
        List<UmlField> champs =
            MemberExtractor.extractFields(AvecConstante.class, UMLVisibility.NONE);

        assertEquals(1, champs.size(), champs.toString());
        assertEquals("composant", champs.get(0).name());
    }

    // --- L'exclusion posée sur la déclaration, lue depuis les réalisations ---

    interface Entree {
        @UMLExclude
        String asString();
    }

    @Test
    void unExcludeSurLInterface_vautPourLOverride() {
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Mnemonic.class, UMLVisibility.PUBLIC);

        assertTrue(methodes.stream().noneMatch(m -> m.name().equals("asString")),
            "asString est exclue sur la déclaration de Entree ; l'override de Mnemonic doit se "
                + "taire sans répéter l'annotation : " + methodes);
        // Le compagnon de contrôle : l'accesseur du composant, lui, n'est exclu nulle part.
        assertTrue(methodes.stream().anyMatch(m -> m.name().equals("keysymMnemonic")),
            methodes.toString());
    }

    abstract static class Parent {
        @UMLExclude
        public void technique() {}
    }

    static class Fille extends Parent {
        @Override
        public void technique() {}

        public void metier() {}
    }

    @Test
    void unExcludeSurLeParent_vautPourLaFille() {
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Fille.class, UMLVisibility.PUBLIC);

        assertTrue(methodes.stream().noneMatch(m -> m.name().equals("technique")),
            methodes.toString());
        assertTrue(methodes.stream().anyMatch(m -> m.name().equals("metier")),
            methodes.toString());
    }

    // --- L'exclusion héritée franchit l'effacement de type ---

    interface Boite<T> {
        @UMLExclude
        void ranger(T objet);

        @UMLExclude
        T lire();
    }

    static class BoiteDeTexte implements Boite<String> {
        @Override
        public void ranger(String objet) {}

        @Override
        public String lire() {
            return "";
        }

        public void metier() {}
    }

    @Test
    void unExcludeSurUneInterfaceGenerique_vautPourLaRealisation() {
        // Le cas que la recherche par signature exacte manquait : « Boite<T>.ranger(T) » s'écrit
        // « ranger(Object) » dans le .class, tandis que la réalisation porte String. L'exclusion
        // se perdait donc pour Comparator, Function, et toute interface métier paramétrée — un
        // membre que l'auteur croit exclu reparaissait sur chacune de ses réalisations.
        //
        // « lire() », sans paramètre, était bien exclue : c'est ce qui rendait le défaut
        // invisible, la fonctionnalité marchant sur la moitié des cas.
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(BoiteDeTexte.class, UMLVisibility.PUBLIC);

        assertEquals(List.of("metier"), methodes.stream().map(UmlMethod::name).toList(),
            methodes.toString());
    }

    interface Rangeuse {
        @UMLExclude
        void ranger(Object objet);
    }

    static class RangeuseStricte implements Rangeuse {
        @Override
        public void ranger(Object objet) {}

        // Une surcharge, et non une redéfinition : le surtype n'a jamais parlé d'elle.
        public void ranger(String texte, int rang) {}
    }

    @Test
    void uneSurchargeDArite_differente_nEstPasTouchee() {
        // La reconnaissance se fait sur le nom, l'arité et l'assignabilité : une surcharge qui
        // prend un argument de plus ne redéfinit rien, et l'exclusion ne doit pas la suivre.
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(RangeuseStricte.class, UMLVisibility.PUBLIC);

        assertEquals(1, methodes.size(), methodes.toString());
        assertEquals(2, methodes.get(0).parameters().size(), methodes.toString());
    }

    static class Homonyme {
        // Statique : elle ne redéfinit rien, l'exclusion d'un homonyme hérité ne la vise pas.
        public static void technique() {}
    }

    @Test
    void uneStatiqueHomonyme_neRedefinitRien_etResteVisible() {
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Homonyme.class, UMLVisibility.PUBLIC);

        assertTrue(methodes.stream().anyMatch(m -> m.name().equals("technique")),
            methodes.toString());
    }
}
