package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.Avertissements;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.relation.AssociationExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HierarchieDesDecisionsTest {

    private static Regles regles(String... motifs) {
        String liste = Arrays.stream(motifs)
            .map(motif -> "\"" + motif + "\"")
            .collect(Collectors.joining(", "));
        return Regles.lues("{ \"hide\": [" + liste + "] }", "umlg.json");
    }

    private static List<String> noms(List<UmlField> champs) {
        return champs.stream().map(UmlField::name).toList();
    }


    record Sac(String logTexte, String garde) {}

    @Test
    void unMotif_neDefaitPasUnComposantDeRecord() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Sac.class, UMLVisibility.NONE, regles("log*"));

        assertEquals(List.of("garde", "logTexte"), noms(champs), champs.toString());
    }

    record SacExclu(@UMLExclude String logTexte, String garde) {}

    @Test
    void surUnComposantDeRecord_seulUmlExcludeGardeLeDernierMot() {
        List<UmlField> champs =
            MemberExtractor.extractFields(SacExclu.class, UMLVisibility.NONE, regles("log*"));

        assertEquals(List.of("garde"), noms(champs), champs.toString());
    }


    @UMLClass
    static class Cible {}

    static class Journal {
        @UMLAssociation(targetMultiplicity = "0..*", notation = UMLNotation.ATTRIBUTE)
        private List<Cible> logCibles;
    }

    static class JournalEnTrait {
        @UMLAssociation(targetMultiplicity = "0..*")
        private List<Cible> logCibles;
    }

    @Test
    void unMotif_neFaitPasDisparaitreUnLienEcritDansLaBoite() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Journal.class, UMLVisibility.NONE, regles("log*"));

        assertEquals(List.of("logCibles"), noms(champs), champs.toString());
        assertEquals("Cible", champs.get(0).type());
        assertEquals("0..*", champs.get(0).multiplicity());
    }

    @Test
    void lesDeuxNotations_survivent_toutesDeux_auMemeMotif() {
        ImmutableList<UmlAssociation> traits =
            new AssociationExtractor().extraire(JournalEnTrait.class);
        List<UmlField> lignes = MemberExtractor.extractFields(Journal.class, UMLVisibility.NONE,
            regles("log*"));

        assertEquals(1, traits.size(), traits.toString());
        assertEquals(1, lignes.size(), lignes.toString());
    }

    static class JournalExclu {
        @UMLExclude
        @UMLAssociation(targetMultiplicity = "0..*", notation = UMLNotation.ATTRIBUTE)
        private List<Cible> logCibles;
    }

    @Test
    void surUneAssociationEcriteDansLaBoite_seulUmlExcludeGardeLeDernierMot() {
        assertTrue(MemberExtractor
            .extractFields(JournalExclu.class, UMLVisibility.NONE, regles("log*")).isEmpty());
    }


    static class Bavarde {
        @UMLInclude
        public String logRetenu;

        public String logTu;

        @UMLInclude(name = "compte")
        public String logRenomme;
    }

    @Test
    void unMotif_neDefaitPasUnInclude_niSonRenommage() {
        List<UmlField> champs =
            MemberExtractor.extractFields(Bavarde.class, UMLVisibility.PUBLIC, regles("log*"));

        assertEquals(List.of("compte", "logRetenu"), noms(champs), champs.toString());
    }

    static class Accesseurs {
        @UMLInclude(asAttribute = true)
        public String logEtat() {
            return "";
        }
    }

    @Test
    void unMotif_neDefaitPasLaNotationAttribut() {
        List<UmlField> champs = MemberExtractor.methodesEnAttributs(Accesseurs.class,
            UMLVisibility.NONE, regles("log*"));

        assertEquals(List.of("logEtat"), noms(champs), champs.toString());
    }

    enum Devise {
        EURO,

        @UMLInclude
        LOGEE,

        LOGISTIQUE
    }

    @Test
    void unMotif_atteintUnLitteral_maisPasUnLitteralInclus() {
        ImmutableList<String> litteraux = MemberExtractor.extractLiterals(Devise.class,
            UMLVisibility.PUBLIC, regles("LOG*"));

        assertEquals(List.of("EURO", "LOGEE"), litteraux, litteraux.toString());
    }


    static class NotationSurUnChamp {
        @UMLInclude(asAttribute = true)
        public String etat;
    }

    private static ImmutableList<String> avertissementsDe(Runnable action) {
        ImmutableList.Builder<String> dits = ImmutableList.builder();
        try (Avertissements.Canal canal = Avertissements.redirectTo(dits::add)) {
            action.run();
        }
        return dits.build();
    }

    @Test
    void asAttributeSurUnChamp_estSignale_commeLeProcesseurLeRefuse() {
        ImmutableList<String> dits = avertissementsDe(
            () -> MemberExtractor.extractFields(NotationSurUnChamp.class, UMLVisibility.PUBLIC));

        assertEquals(1, dits.size(), dits.toString());
        assertTrue(dits.get(0).contains("@UMLInclude(asAttribute = true) is for methods; a field "
            + "is already an attribute."), dits.get(0));
        assertTrue(dits.get(0).contains("NotationSurUnChamp.etat"), dits.get(0));
    }

    @Test
    void unChampOrdinaire_neDitRien() {
        assertTrue(avertissementsDe(
            () -> MemberExtractor.extractFields(Bavarde.class, UMLVisibility.PUBLIC)).isEmpty());
    }
}
