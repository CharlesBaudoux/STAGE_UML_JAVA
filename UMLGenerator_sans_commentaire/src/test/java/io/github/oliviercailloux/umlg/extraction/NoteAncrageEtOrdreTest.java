package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlNote;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoteAncrageEtOrdreTest {

    private static List<String> textes(UmlEntity entity) {
        return entity.notes().stream().map(UmlNote::text).toList();
    }


    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Personne {

        @UMLNote(text = "la note d'un attribut ordinaire")
        public int age;

        @UMLInclude(asAttribute = true)
        @UMLNote(text = "la note d'une methode en attribut")
        public String nom() {
            return "";
        }

        @UMLNote(text = "la note d'une methode ordinaire")
        public void verifier() {}
    }

    @Test
    void noteSurUneMethodeEnNotationAttribut_estRetenue() {
        UmlEntity entity = EntityExtractor.extract(Personne.class);

        assertEquals(List.of("la note d'un attribut ordinaire",
            "la note d'une methode en attribut", "la note d'une methode ordinaire"),
            textes(entity));
    }

    @Test
    void noteSurUneMethodeEnNotationAttribut_sAccrocheAuNomDessine() {
        UmlEntity entity = EntityExtractor.extract(Personne.class);

        UmlNote note = entity.notes().stream()
            .filter(n -> n.text().contains("methode en attribut")).findFirst().orElseThrow();

        assertEquals("nom", note.anchor());
        assertEquals(List.of("age", "nom"),
            entity.fields().stream().map(UmlField::name).toList());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Renommee {

        @UMLInclude(asAttribute = true, name = "identifiant")
        @UMLNote(text = "sur le nom du diagramme")
        public String id() {
            return "";
        }
    }

    @Test
    void noteSurUneMethodeRenommee_sAccrocheAuNomDuDiagramme() {
        UmlEntity entity = EntityExtractor.extract(Renommee.class);

        assertEquals("identifiant", entity.notes().get(0).anchor());
    }


    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Desordonnee {

        @UMLNote(text = "zebre")
        public int zebre;

        @UMLNote(text = "autruche")
        public int autruche;

        @UMLNote(text = "zoo")
        public void zoo() {}

        @UMLNote(text = "arche")
        public void arche() {}
    }

    private static List<String> notesSous(Regles regles) {
        ImmutableList<UmlField> champs =
            MemberExtractor.extractFields(Desordonnee.class, UMLVisibility.PUBLIC, regles);
        ImmutableList<UmlMethod> methodes =
            MemberExtractor.extractMethods(Desordonnee.class, UMLVisibility.PUBLIC, regles);

        return NoteExtractor.extraire(Desordonnee.class, champs, methodes, regles).stream()
            .map(UmlNote::text).toList();
    }

    @Test
    void parDefaut_lesBullesSuiventLAlphabet() {
        assertEquals(List.of("autruche", "zebre", "arche", "zoo"), notesSous(Regles.aucune()));
    }

    @Test
    void surDemande_lesBullesSuiventLOrdreDuCode() {
        Regles regles = Regles.lues("{ \"order\": \"declaration\" }", "umlg.json");

        assertEquals(List.of("zebre", "autruche", "zoo", "arche"), notesSous(regles));
    }
}
