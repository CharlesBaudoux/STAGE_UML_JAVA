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

// La hiérarchie des décisions, éprouvée là où deux mécanismes se croisent.
//
// L'ordre est le même partout dans la bibliothèque : @UMLExclude d'abord, @UMLInclude ensuite,
// puis les deux défauts généraux — la stratégie de la classe et les motifs « hide » du fichier de
// règles, qui sont au même rang parce qu'ils sont de même nature : des réglages d'ensemble, que
// personne n'a pris sur ce membre-ci.
//
// Les quatre compartiments réimplémentaient chacun leur propre séquence de gardes, et les quatre
// différaient. Les tests ne le voyaient pas : chacun exerçait un mécanisme seul, jamais un motif
// ET un record, un motif ET une association. Ce fichier ne contient que des croisements.
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

    // --- Un motif contre un composant de record ---

    // Les cobayes ne portent pas @UMLClass, sauf les cibles d'association, qui doivent l'être
    // pour être visables : MemberExtractor n'en a pas besoin, et les tests d'intégration scannent
    // tout target/test-classes.
    record Sac(String logTexte, String garde) {}

    @Test
    void unMotif_neDefaitPasUnComposantDeRecord() {
        // Un composant est la définition même du record : une boîte qui le tairait ne dirait plus
        // ce que le record EST. Un motif est un défaut général, comme la stratégie, et il ne
        // défait pas plus qu'elle une définition.
        List<UmlField> champs =
            MemberExtractor.extractFields(Sac.class, UMLVisibility.NONE, regles("log*"));

        assertEquals(List.of("garde", "logTexte"), noms(champs), champs.toString());
    }

    record SacExclu(@UMLExclude String logTexte, String garde) {}

    @Test
    void surUnComposantDeRecord_seulUmlExcludeGardeLeDernierMot() {
        // L'autre moitié de la règle : ce qu'un défaut général ne peut pas faire, le veto
        // individuel le peut — sans quoi plus rien ne pourrait retirer un composant.
        List<UmlField> champs =
            MemberExtractor.extractFields(SacExclu.class, UMLVisibility.NONE, regles("log*"));

        assertEquals(List.of("garde"), noms(champs), champs.toString());
    }

    // --- Un motif contre une association écrite dans la boîte ---

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
        // La documentation présente ARROW et ATTRIBUTE comme deux écritures du même lien. Sous un
        // motif, le trait restait et la ligne disparaissait : changer de notation effaçait le
        // lien, ce que rien n'annonçait.
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

    // --- Le même croisement, sur les trois autres compartiments ---

    static class Bavarde {
        @UMLInclude
        public String logRetenu;

        public String logTu;

        @UMLInclude(name = "compte")
        public String logRenomme;
    }

    @Test
    void unMotif_neDefaitPasUnInclude_niSonRenommage() {
        // La décision individuelle bat le réglage général, et le nom qu'elle donne vaut aussi :
        // les deux moitiés de la même question, posées au même endroit.
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
        // asAttribute est écrit dans un @UMLInclude : la demande est individuelle, et le motif ne
        // la défait pas plus ici qu'ailleurs.
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
        // Un littéral échappait à tout ce qui vaut pour les autres membres : aucun motif ne
        // l'atteignait jamais, et rien ne le documentait.
        ImmutableList<String> litteraux = MemberExtractor.extractLiterals(Devise.class,
            UMLVisibility.PUBLIC, regles("LOG*"));

        assertEquals(List.of("EURO", "LOGEE"), litteraux, litteraux.toString());
    }

    // --- Ce que la génération dit maintenant de asAttribute sur un attribut ---

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
        // Le processeur arrête la compilation sur ce code ; la génération l'ignorait sans un mot.
        // Le même code recevait donc deux réponses selon qu'on le compilait avec le processeur ou
        // qu'on le dessinait sans lui. Le texte est celui de l'erreur du processeur, au mot près.
        ImmutableList<String> dits = avertissementsDe(
            () -> MemberExtractor.extractFields(NotationSurUnChamp.class, UMLVisibility.PUBLIC));

        assertEquals(1, dits.size(), dits.toString());
        assertTrue(dits.get(0).contains("@UMLInclude(asAttribute = true) is for methods; a field "
            + "is already an attribute."), dits.get(0));
        assertTrue(dits.get(0).contains("NotationSurUnChamp.etat"), dits.get(0));
    }

    @Test
    void unChampOrdinaire_neDitRien() {
        // La retenue compte autant que le signalement : un avertissement qui se déclencherait sur
        // du code ordinaire apprendrait à ne plus les lire.
        assertTrue(avertissementsDe(
            () -> MemberExtractor.extractFields(Bavarde.class, UMLVisibility.PUBLIC)).isEmpty());
    }
}
