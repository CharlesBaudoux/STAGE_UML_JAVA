package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

// Ce que les boîtes montrent, sous quel nom, dans quel ordre, et sous quelle forme.
//
// Quatre libertés récentes, et une même raison d'être : permettre au diagramme engendré de
// rejoindre le diagramme de référence d'un projet — celui du README, tenu à la main — sans
// réécrire le code. Renommer un membre (@UMLInclude(name = ...)), garder l'ordre d'écriture
// (« order declaration »), abréger les signatures (« hide parameter names »), et taire un membre
// nommément (« hide field Classe.nom »), même inclus.
class AffichageDesMembresTest {

    private static Regles regles(String... lignes) {
        return Regles.lues(ImmutableList.copyOf(lignes), "umlg.rules");
    }

    // --- Le renommage : @UMLInclude(name = ...) ---

    // Les cobayes ne portent pas @UMLClass : MemberExtractor n'en a pas besoin, et les tests
    // d'intégration scannent tout target/test-classes — un cobaye délibérément fautif qui y
    // serait annoté ferait échouer leur génération. Seule Cible l'est : une association exige
    // que sa classe visée soit du diagramme.
    static class Abrege {
        @UMLInclude(name = "xKeycodeByXKeyName")
        public int codeByName;

        @UMLInclude(name = "byKeysymMnemonic")
        public int byMnemonic() {
            return 0;
        }
    }

    @Test
    void unAttributRenomme_paraitSousLeNomDuDiagramme() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Abrege.class, UMLVisibility.PUBLIC);

        assertEquals("xKeycodeByXKeyName", champs.get(0).name());
    }

    @Test
    void uneMethodeRenommee_paraitSousLeNomDuDiagramme() {
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Abrege.class, UMLVisibility.PUBLIC);

        assertEquals("byKeysymMnemonic", methodes.get(0).name());
    }

    static class RenommageVide {
        @UMLInclude(name = "   ")
        public int champ;
    }

    @Test
    void unNomFaitDEspaces_estRefuseEnNommantLeChamp() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(RenommageVide.class, UMLVisibility.PUBLIC));

        assertTrue(refus.getMessage().contains("whitespace"), refus.getMessage());
        assertTrue(refus.getMessage().contains("champ"), refus.getMessage());
    }

    @UMLClass
    static class Cible {}

    static class DeuxNoms {
        @UMLInclude(name = "unNom")
        @UMLAssociation(target = Cible.class, targetRole = "unAutre",
            notation = UMLNotation.ATTRIBUTE)
        public Cible champ;
    }

    @Test
    void targetRoleEtName_surLaMemeLigne_sontRefuses() {
        // Deux noms pour la même ligne : rien ne dirait lequel l'emporte, et la bibliothèque ne
        // choisit pas à la place d'un code qui se contredit.
        var refus = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(DeuxNoms.class, UMLVisibility.PUBLIC));

        assertTrue(refus.getMessage().contains("two names"), refus.getMessage());
    }

    // --- L'ordre de déclaration : « order declaration » ---

    static class Desordonnee {
        public int zebre;
        public int autruche;
        public int merle;

        public void zoo() {}

        public void arche() {}
    }

    @Test
    void parDefaut_lAlphabetRange() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Desordonnee.class, UMLVisibility.PUBLIC);

        assertEquals(List.of("autruche", "merle", "zebre"),
            champs.stream().map(UmlField::name).toList());
    }

    @Test
    void surDemande_lOrdreDuCodeEstGarde() {
        // La réflexion ignore cet ordre ; il est relu dans le fichier .class, où les tables de
        // membres suivent le source.
        Regles regles = regles("order declaration");

        List<UmlField> champs =
            MemberExtractor.extractFields(Desordonnee.class, UMLVisibility.PUBLIC, regles);
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Desordonnee.class, UMLVisibility.PUBLIC, regles);

        assertEquals(List.of("zebre", "autruche", "merle"),
            champs.stream().map(UmlField::name).toList());
        assertEquals(List.of("zoo", "arche"),
            methodes.stream().map(UmlMethod::name).toList());
    }

    static class Surchargee {
        public void faire(String texte) {}

        public void faire(int nombre) {}

        public void avant() {}
    }

    @Test
    void lesSurcharges_gardentAussiLeurOrdre() {
        // Deux surcharges partagent un nom : seul le descripteur les départage, et l'ordre du
        // source doit survivre à cette ambiguïté.
        List<UmlMethod> methodes = MemberExtractor.extractMethods(Surchargee.class,
            UMLVisibility.PUBLIC, regles("order declaration"));

        assertEquals("faire", methodes.get(0).name());
        assertEquals("String", methodes.get(0).parameters().get(0).type());
        assertEquals("faire", methodes.get(1).name());
        assertEquals("int", methodes.get(1).parameters().get(0).type());
        assertEquals("avant", methodes.get(2).name());
    }

    // --- Les signatures courtes : « hide parameter names » ---

    static class Bavarde {
        public void configurer(String cheminDuFichier, int nombreDeColonnes) {}
    }

    @Test
    void lesNomsDesParametres_seTaisentSurDemande() {
        List<UmlMethod> methodes = MemberExtractor.extractMethods(Bavarde.class,
            UMLVisibility.PUBLIC, regles("hide parameter names"));

        // Le type reste — c'est lui qui porte l'information — mais le nom disparaît, comme dans
        // un diagramme tenu à la main : « configurer(String, int) ».
        assertTrue(methodes.get(0).parameters().stream().noneMatch(p -> p.nomme()),
            methodes.toString());
        assertEquals("String", methodes.get(0).parameters().get(0).type());
    }

    // --- La désignation nommément : « hide field Classe.nom » ---

    static class Parent {
        @UMLInclude
        public String string;

        @UMLInclude
        public String svg;

        @UMLInclude
        public String reste;
    }

    @Test
    void unMembreDesigneNommement_sortMemeInclus() {
        // Le cas qui a motivé la forme : des classes déclarées dans umlg.rules redisent le
        // contenu du parent, et la boîte parente doit pouvoir se taire sans qu'on retire les
        // @UMLInclude du code — ils servent aux autres vues.
        List<UmlField> champs = MemberExtractor.extractFields(Parent.class, UMLVisibility.NONE,
            regles("hide field Parent.string, Parent.svg"));

        assertEquals(List.of("reste"), champs.stream().map(UmlField::name).toList());
    }

    @Test
    void unMotifGeneral_nEmportePasSurUnInclude() {
        // La règle de toujours : la décision individuelle bat la règle générale. Seule la
        // désignation nommément, aussi individuelle que l'annotation, fait exception.
        List<UmlField> champs = MemberExtractor.extractFields(Parent.class, UMLVisibility.NONE,
            regles("hide field s*"));

        assertEquals(List.of("reste", "string", "svg"),
            champs.stream().map(UmlField::name).toList());
    }
}
