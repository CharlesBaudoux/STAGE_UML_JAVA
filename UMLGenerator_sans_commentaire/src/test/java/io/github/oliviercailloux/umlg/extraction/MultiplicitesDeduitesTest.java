package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MultiplicitesDeduitesTest {

    private static final Regles BRACKETS =
        Regles.lues("""
            { "multiplicity": "brackets" }
            """, "umlg.json");

    static class Cobaye {
        public Set<String> aliases;
        public Optional<Integer> ucp;
        public Map<String, Integer> table;
        public String nom;

        @UMLMultiplicity("1..*")
        public List<String> obligatoires;

        public Set<String> names() {
            return Set.of();
        }
    }

    private static UmlField champ(String nom) {
        return MemberExtractor.extractFields(Cobaye.class, UMLVisibility.PUBLIC, BRACKETS)
            .stream().filter(f -> f.name().equals(nom)).findFirst().orElseThrow();
    }

    @Test
    void unSet_secritElementEtEtoile() {
        assertEquals("String", champ("aliases").type());
        assertEquals("0..*", champ("aliases").cardinality());
    }

    @Test
    void unOptional_secritElementDeballeEtZeroOuUn() {
        assertEquals("int", champ("ucp").type());
        assertEquals("0..1", champ("ucp").cardinality());
    }

    @Test
    void uneMap_resteEcriteEnEntier() {
        assertEquals("Map<String, Integer>", champ("table").type());
        assertEquals("", champ("table").cardinality());
    }

    @Test
    void unTypeSimple_neRecoitRien() {
        assertEquals("String", champ("nom").type());
        assertEquals("", champ("nom").cardinality());
    }

    @Test
    void uneMultipliciteDeclaree_lEmporteSurLaDeduction() {
        assertEquals("1..*", champ("obligatoires").cardinality());
        assertEquals("String", champ("obligatoires").type());
    }

    @Test
    void sansLaRegle_riennEstTraduit() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Cobaye.class, UMLVisibility.PUBLIC, Regles.aucune());
        UmlField aliases =
            champs.stream().filter(f -> f.name().equals("aliases")).findFirst().orElseThrow();

        assertEquals("Set<String>", aliases.type());
        assertEquals("", aliases.cardinality());
    }

    @Test
    void unRetourDeMethode_estTraduitAussi() {
        List<UmlMethod> methodes =
            MemberExtractor.extractMethods(Cobaye.class, UMLVisibility.PUBLIC, BRACKETS);
        UmlMethod names =
            methodes.stream().filter(m -> m.name().equals("names")).findFirst().orElseThrow();

        assertEquals("String", names.returnType());
        assertEquals("0..*", names.cardinality());
    }

    @Test
    void uneAutreFormeQueBrackets_estRefuseeEnNommantLaCle() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> Regles.lues("""
                { "multiplicity": "stars" }
                """, "umlg.json"));

        assertTrue(refus.getMessage().contains("multiplicity"), refus.getMessage());
        assertTrue(refus.getMessage().contains("brackets"), refus.getMessage());
    }

    @Test
    void laRegleEnDouble_estRefuseeParLAnalyseur() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> Regles.lues("""
                { "multiplicity": "brackets", "multiplicity": "brackets" }
                """,
                "umlg.json"));

        assertTrue(refus.getMessage().contains("multiplicity"), refus.getMessage());
        assertTrue(refus.getMessage().contains("umlg.json"), refus.getMessage());
    }
}
