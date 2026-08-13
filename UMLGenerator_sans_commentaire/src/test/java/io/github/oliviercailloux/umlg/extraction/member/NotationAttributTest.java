package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotationAttributTest {

    interface Clavier {
        @UMLInclude(asAttribute = true, name = "xKeycodeByXKeyName")
        Map<String, Short> codeByName();

        @UMLInclude
        Clavier withoutAliases();

        String canonical(String keyName);
    }

    @Test
    void unAccesseurEnNotationAttribut_paraitSansParentheses() {
        List<UmlField> attributs = MemberExtractor.methodesEnAttributs(Clavier.class,
            UMLVisibility.PUBLIC, Regles.aucune());

        assertEquals(1, attributs.size(), attributs.toString());
        assertEquals("xKeycodeByXKeyName", attributs.get(0).name());
        assertEquals("Map<String, Short>", attributs.get(0).type());
    }

    @Test
    void laMethodeConvertie_quitteLeCompartimentDesMethodes() {
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Clavier.class, UMLVisibility.PUBLIC);

        assertTrue(methodes.stream().noneMatch(m -> m.name().equals("xKeycodeByXKeyName")),
            methodes.toString());
        assertTrue(methodes.stream().noneMatch(m -> m.name().equals("codeByName")),
            methodes.toString());
        assertTrue(methodes.stream().anyMatch(m -> m.name().equals("withoutAliases")),
            methodes.toString());
    }

    static class Fabrique {
        @UMLInclude(asAttribute = true)
        public static Fabrique instance() {
            return new Fabrique();
        }
    }

    @Test
    void unAccesseurStatique_gardeSonSoulignement() {
        List<UmlField> attributs = MemberExtractor.methodesEnAttributs(Fabrique.class,
            UMLVisibility.PUBLIC, Regles.aucune());

        assertTrue(attributs.get(0).isStatic(), attributs.toString());
    }

    static class AvecParametre {
        @UMLInclude(asAttribute = true)
        public String canonical(String keyName) {
            return keyName;
        }
    }

    @Test
    void uneMethodeAParametres_estRefuseeEnNommantLaMethode() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.methodesEnAttributs(AvecParametre.class, UMLVisibility.PUBLIC,
                Regles.aucune()));

        assertTrue(refus.getMessage().contains("takes parameters"), refus.getMessage());
        assertTrue(refus.getMessage().contains("canonical"), refus.getMessage());
    }

    static class SansRetour {
        @UMLInclude(asAttribute = true)
        public void valider() {}
    }

    @Test
    void uneMethodeSansRetour_estRefusee() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.methodesEnAttributs(SansRetour.class, UMLVisibility.PUBLIC,
                Regles.aucune()));

        assertTrue(refus.getMessage().contains("returns nothing"), refus.getMessage());
    }

    static class Masquee {
        @UMLInclude(asAttribute = true)
        @UMLExclude
        public String etat() {
            return "";
        }
    }

    @Test
    void seulUmlExcludeLEmporteSurLaNotation() {
        Regles regles = Regles.lues("""
            { "hide": ["*tat"] }
            """, "umlg.json");

        assertTrue(MemberExtractor.methodesEnAttributs(Masquee.class, UMLVisibility.PUBLIC, regles)
            .isEmpty());
    }

    @Test
    void unMotifQualifie_estRefuseEnRenvoyantVersUmlExclude() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> Regles.lues("""
                { "hide": ["Masquee.etat"] }
                """, "umlg.json"));

        assertTrue(refus.getMessage().contains("@UMLExclude"), refus.getMessage());
    }
}
