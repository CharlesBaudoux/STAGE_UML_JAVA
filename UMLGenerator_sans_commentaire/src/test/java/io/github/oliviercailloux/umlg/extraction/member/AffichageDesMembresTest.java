package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class AffichageDesMembresTest {

    private static Regles regles(String json) {
        return Regles.lues(json, "umlg.json");
    }

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
        var refus = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(DeuxNoms.class, UMLVisibility.PUBLIC));

        assertTrue(refus.getMessage().contains("two names"), refus.getMessage());
    }

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
        Regles regles = regles("""
            { "order": "declaration" }
            """);

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
        List<UmlMethod> methodes = MemberExtractor.extractMethods(Surchargee.class,
            UMLVisibility.PUBLIC, regles("""
                { "order": "declaration" }
                """));

        assertEquals("faire", methodes.get(0).name());
        assertEquals("String", methodes.get(0).parameters().get(0).type());
        assertEquals("faire", methodes.get(1).name());
        assertEquals("int", methodes.get(1).parameters().get(0).type());
        assertEquals("avant", methodes.get(2).name());
    }

    static class Bavarde {
        public void configurer(String cheminDuFichier, int nombreDeColonnes) {}
    }

    @Test
    void lesNomsDesParametres_seTaisentSurDemande() {
        List<UmlMethod> methodes = MemberExtractor.extractMethods(Bavarde.class,
            UMLVisibility.PUBLIC, regles("""
                { "omit": ["parameter names"] }
                """));

        assertTrue(methodes.get(0).parameters().stream().noneMatch(p -> p.nomme()),
            methodes.toString());
        assertEquals("String", methodes.get(0).parameters().get(0).type());
    }

    static class Parent {
        @UMLInclude
        public String string;

        @UMLInclude
        public String svg;

        @UMLInclude
        public String reste;
    }

    @Test
    void unMotif_nEmportePasSurUnInclude() {
        List<UmlField> champs = MemberExtractor.extractFields(Parent.class, UMLVisibility.NONE,
            regles("""
                { "hide": ["s*"] }
                """));

        assertEquals(List.of("reste", "string", "svg"),
            champs.stream().map(UmlField::name).toList());
    }

    @Test
    void unMotifQualifie_estRefuseEnRenvoyantVersUmlExclude() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> regles("""
                { "hide": ["Parent.string"] }
                """));

        assertTrue(refus.getMessage().contains("@UMLExclude"), refus.getMessage());
    }
}
