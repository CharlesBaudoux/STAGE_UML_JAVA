package io.github.oliviercailloux.umlg.rendering.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlNote;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Le balisage Creole qui ne s'ouvre qu'en tête d'une ligne dessinée.
//
// PlantUML lit deux grammaires de balisage. La première s'ouvre n'importe où dans une ligne, par
// des marqueurs doublés — « **gras** », « [[lien]] » —, et PlantUmlEscapeTest la couvre. La
// seconde ne s'applique qu'au premier caractère d'une ligne rendue : « # » et « * » une liste à
// puces, « = » un titre, « | » une ligne de tableau. Elle n'était pas couverte, et elle passait :
// une note valant « # Attention » se dessinait « 1. Attention », un nom de boîte valant
// « = titre » perdait son signe égal.
//
// Ces tests vont jusqu'au SVG : c'est le seul endroit où l'on voit ce que le lecteur voit. Une
// entité numérique présente dans la source est exactement ce qu'on attend — c'est à l'écran
// qu'elle redevient le caractère écrit.
class PlantUmlDebutDeLigneTest {

    // Les quatre caractères concernés, chacun en tête d'un texte plausible.
    private static final List<String> TEXTES_A_RISQUE = List.of(
        "# Attention", "* voir le contrat", "= titre", "|a|b|");

    private static UmlEntity nommee(String nom) {
        return new UmlEntity(nom, "", "", UMLVisibility.PUBLIC, List.of(), List.of());
    }

    // --- Position 1 : le nom d'une boîte ---

    @ParameterizedTest
    @ValueSource(strings = {"# Attention", "* voir le contrat", "= titre", "|a|b|"})
    void nomDeBoite_gardeSonPremierCaractere(String nom) throws Exception {
        String rendu = PlantUmlAssert.texteRendu(nommee(nom));

        assertTrue(rendu.contains(nom),
            "Le nom « " + nom + " » a été lu comme du balisage. Texte dessiné :\n" + rendu);
    }

    // --- Position 2 : le nom d'un rectangle de groupe ---

    @ParameterizedTest
    @ValueSource(strings = {"# Attention", "* voir le contrat", "= titre", "|a|b|"})
    void nomDeGroupe_gardeSonPremierCaractere(String nom) throws Exception {
        UmlEntity rangee = UmlEntity.builder("com.exemple.Facture", "Facture")
            .group(new UmlPackage(nom, "")).build();

        String rendu = PlantUmlAssert.texteRendu(rangee);

        assertTrue(rendu.contains(nom),
            "Le nom de groupe « " + nom + " » a été lu comme du balisage :\n" + rendu);
    }

    // --- Position 3 : le titre du diagramme ---

    @Test
    void titreDuDiagramme_gardeSesSignesEgal() throws Exception {
        // Le cas cité par l'audit : « === Facturation === » se dessinait « Facturation === »,
        // les trois premiers signes égal consommés comme un titre Creole.
        UmlDiagram reglages = new UmlDiagram("=== Facturation ===", UMLFlow.TOP_TO_BOTTOM, true);
        String source =
            PlantUmlAssert.documentPour(reglages, nommee("Facture"));

        PlantUmlAssert.assertCompile(source);
        assertTrue(source.contains("title &#61;== Facturation ==="),
            "Seul le premier caractère est encodé, et il suffit :\n" + source);
    }

    // --- Position 4 : la note d'une seule ligne ---

    @ParameterizedTest
    @ValueSource(strings = {"# Attention", "* voir le contrat", "= titre", "|a|b|"})
    void noteSurClasse_gardeSonPremierCaractere(String texte) throws Exception {
        UmlEntity avecNote = UmlEntity.builder("com.exemple.Facture", "Facture")
            .notes(ImmutableList.of(UmlNote.surClasse(texte, UMLDirection.RIGHT))).build();

        String rendu = PlantUmlAssert.texteRendu(avecNote);

        assertTrue(rendu.contains(texte),
            "La note « " + texte + " » a été lue comme du balisage :\n" + rendu);
    }

    // --- Position 5 : un membre dont la visibilité est tue ---

    @ParameterizedTest
    @ValueSource(strings = {"#total", "*total", "=total", "|total"})
    void membreSansVisibilite_gardeSonPremierCaractere(String nom) throws Exception {
        // Sous « omit: [visibility] », le symbole de visibilité est vide : le nom du membre
        // devient le premier caractère de la ligne dessinée.
        UmlEntity boite = new UmlEntity("Facture", "", "", UMLVisibility.PUBLIC,
            List.of(new UmlField(nom, "int", "", false)), List.of());

        String rendu = PlantUmlAssert.texteRendu(boite);

        assertTrue(rendu.contains(nom),
            "Le membre « " + nom + " » a perdu son premier caractère :\n" + rendu);
    }

    @Test
    void visibiliteProtegee_resteLeDieseQuePlantUmlAttend() {
        // Le contre-exemple qui borne la correction : le « # » d'un membre protégé est écrit par
        // la bibliothèque, et c'est ainsi que PlantUML lit la visibilité. L'encoder afficherait
        // un dièse littéral au lieu de la notation UML.
        UmlEntity boite = new UmlEntity("Facture", "", "", UMLVisibility.PUBLIC,
            List.of(new UmlField("total", "int", "#", false)), List.of());

        assertTrue(PlantUmlAssert.documentPour(boite).contains("  # total : int"),
            PlantUmlAssert.documentPour(boite));
    }

    // --- Position 6 : un littéral d'énumération ---

    @ParameterizedTest
    @ValueSource(strings = {"#EURO", "*EURO", "=EURO", "|EURO"})
    void literalDEnumeration_gardeSonPremierCaractere(String literal) throws Exception {
        // La position que le relevé de l'audit ne cite pas, et qui souffre du même défaut : un
        // littéral occupe à lui seul sa ligne, sans visibilité ni type devant lui.
        UmlEntity devise = UmlEntity.builder("com.exemple.Devise", "Devise")
            .kind(UmlEntityKind.ENUM).literals(ImmutableList.of(literal, "DOLLAR")).build();

        String rendu = PlantUmlAssert.texteRendu(devise);

        assertTrue(rendu.contains(literal),
            "Le littéral « " + literal + " » a perdu son premier caractère :\n" + rendu);
    }

    // --- L'encodeur lui-même ---

    @Test
    void debutDeLigne_nEncodeQueLesMarqueursDeLigne() {
        // Encoder toujours le premier caractère aurait suffi à fermer la brèche — c'est ce que
        // fait noteBloc, pour une autre raison — mais aurait rendu illisible la source engendrée,
        // que le projet conserve précisément pour qu'on puisse la relire et la comparer.
        assertEquals("Compte bancaire", PlantUmlEscaper.debutDeLigne("Compte bancaire"));
        assertEquals("&#35; Attention", PlantUmlEscaper.debutDeLigne("# Attention"));
        assertEquals("&#42; voir", PlantUmlEscaper.debutDeLigne("* voir"));
        assertEquals("&#61; titre", PlantUmlEscaper.debutDeLigne("= titre"));
        assertEquals("&#124;a|b|", PlantUmlEscaper.debutDeLigne("|a|b|"));
    }

    @Test
    void debutDeLigne_faitAussiCeQueFaitLabel() {
        // Ce n'est pas une seconde grammaire : c'est label(), plus la neutralisation du premier
        // caractère. Les paires restent cassées, les chevrons encodés.
        assertEquals("&#42;*gras&#42;*", PlantUmlEscaper.debutDeLigne("**gras**"));
        assertEquals("&#60;b&#62;", PlantUmlEscaper.debutDeLigne("<b>"));
        assertEquals("a\\nb", PlantUmlEscaper.debutDeLigne("a\nb"));
    }

    @Test
    void debutDeLigne_regardeLeTexteEchappeEtNonLeTexteEcrit() {
        // Un caractère de contrôle est supprimé par l'échappement : c'est le « # » qui se
        // retrouve en tête de la ligne dessinée, et c'est donc lui qu'il faut neutraliser. Poser
        // la question au texte d'origine aurait conclu « pas un marqueur ».
        assertEquals("&#35;total", PlantUmlEscaper.debutDeLigne("\1#total"));
    }

    @Test
    void debutDeLigne_laisseVideUnTexteQueLEchappementVide() {
        assertEquals("", PlantUmlEscaper.debutDeLigne("\1\2"));
        assertEquals("", PlantUmlEscaper.debutDeLigne(""));
    }

    @Test
    void chaqueTexteARisque_produitUnDocumentValide() {
        // Le filet : quel que soit le texte, la source reste une source que PlantUML accepte.
        for (String texte : TEXTES_A_RISQUE) {
            PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(nommee(texte)));
        }
    }
}
