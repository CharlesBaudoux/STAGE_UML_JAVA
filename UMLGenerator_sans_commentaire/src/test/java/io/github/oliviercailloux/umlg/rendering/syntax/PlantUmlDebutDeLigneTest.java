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

class PlantUmlDebutDeLigneTest {

    private static final List<String> TEXTES_A_RISQUE = List.of(
        "# Attention", "* voir le contrat", "= titre", "|a|b|");

    private static UmlEntity nommee(String nom) {
        return new UmlEntity(nom, "", "", UMLVisibility.PUBLIC, List.of(), List.of());
    }


    @ParameterizedTest
    @ValueSource(strings = {"# Attention", "* voir le contrat", "= titre", "|a|b|"})
    void nomDeBoite_gardeSonPremierCaractere(String nom) throws Exception {
        String rendu = PlantUmlAssert.texteRendu(nommee(nom));

        assertTrue(rendu.contains(nom),
            "Le nom « " + nom + " » a été lu comme du balisage. Texte dessiné :\n" + rendu);
    }


    @ParameterizedTest
    @ValueSource(strings = {"# Attention", "* voir le contrat", "= titre", "|a|b|"})
    void nomDeGroupe_gardeSonPremierCaractere(String nom) throws Exception {
        UmlEntity rangee = UmlEntity.builder("com.exemple.Facture", "Facture")
            .group(new UmlPackage(nom, "")).build();

        String rendu = PlantUmlAssert.texteRendu(rangee);

        assertTrue(rendu.contains(nom),
            "Le nom de groupe « " + nom + " » a été lu comme du balisage :\n" + rendu);
    }


    @Test
    void titreDuDiagramme_gardeSesSignesEgal() throws Exception {
        UmlDiagram reglages = new UmlDiagram("=== Facturation ===", UMLFlow.TOP_TO_BOTTOM, true);
        String source =
            PlantUmlAssert.documentPour(reglages, nommee("Facture"));

        PlantUmlAssert.assertCompile(source);
        assertTrue(source.contains("title &#61;== Facturation ==="),
            "Seul le premier caractère est encodé, et il suffit :\n" + source);
    }


    @ParameterizedTest
    @ValueSource(strings = {"# Attention", "* voir le contrat", "= titre", "|a|b|"})
    void noteSurClasse_gardeSonPremierCaractere(String texte) throws Exception {
        UmlEntity avecNote = UmlEntity.builder("com.exemple.Facture", "Facture")
            .notes(ImmutableList.of(UmlNote.onClass(texte, UMLDirection.RIGHT))).build();

        String rendu = PlantUmlAssert.texteRendu(avecNote);

        assertTrue(rendu.contains(texte),
            "La note « " + texte + " » a été lue comme du balisage :\n" + rendu);
    }


    @ParameterizedTest
    @ValueSource(strings = {"#total", "*total", "=total", "|total"})
    void membreSansVisibilite_gardeSonPremierCaractere(String nom) throws Exception {
        UmlEntity boite = new UmlEntity("Facture", "", "", UMLVisibility.PUBLIC,
            List.of(new UmlField(nom, "int", "", false)), List.of());

        String rendu = PlantUmlAssert.texteRendu(boite);

        assertTrue(rendu.contains(nom),
            "Le membre « " + nom + " » a perdu son premier caractère :\n" + rendu);
    }

    @Test
    void visibiliteProtegee_resteLeDieseQuePlantUmlAttend() {
        UmlEntity boite = new UmlEntity("Facture", "", "", UMLVisibility.PUBLIC,
            List.of(new UmlField("total", "int", "#", false)), List.of());

        assertTrue(PlantUmlAssert.documentPour(boite).contains("  # total : int"),
            PlantUmlAssert.documentPour(boite));
    }


    @ParameterizedTest
    @ValueSource(strings = {"#EURO", "*EURO", "=EURO", "|EURO"})
    void literalDEnumeration_gardeSonPremierCaractere(String literal) throws Exception {
        UmlEntity devise = UmlEntity.builder("com.exemple.Devise", "Devise")
            .kind(UmlEntityKind.ENUM).literals(ImmutableList.of(literal, "DOLLAR")).build();

        String rendu = PlantUmlAssert.texteRendu(devise);

        assertTrue(rendu.contains(literal),
            "Le littéral « " + literal + " » a perdu son premier caractère :\n" + rendu);
    }


    @Test
    void debutDeLigne_nEncodeQueLesMarqueursDeLigne() {
        assertEquals("Compte bancaire", PlantUmlEscaper.debutDeLigne("Compte bancaire"));
        assertEquals("&#35; Attention", PlantUmlEscaper.debutDeLigne("# Attention"));
        assertEquals("&#42; voir", PlantUmlEscaper.debutDeLigne("* voir"));
        assertEquals("&#61; titre", PlantUmlEscaper.debutDeLigne("= titre"));
        assertEquals("&#124;a|b|", PlantUmlEscaper.debutDeLigne("|a|b|"));
    }

    @Test
    void debutDeLigne_faitAussiCeQueFaitLabel() {
        assertEquals("&#42;*gras&#42;*", PlantUmlEscaper.debutDeLigne("**gras**"));
        assertEquals("&#60;b&#62;", PlantUmlEscaper.debutDeLigne("<b>"));
        assertEquals("a\\nb", PlantUmlEscaper.debutDeLigne("a\nb"));
    }

    @Test
    void debutDeLigne_regardeLeTexteEchappeEtNonLeTexteEcrit() {
        assertEquals("&#35;total", PlantUmlEscaper.debutDeLigne("\1#total"));
    }

    @Test
    void debutDeLigne_laisseVideUnTexteQueLEchappementVide() {
        assertEquals("", PlantUmlEscaper.debutDeLigne("\1\2"));
        assertEquals("", PlantUmlEscaper.debutDeLigne(""));
    }

    @Test
    void chaqueTexteARisque_produitUnDocumentValide() {
        for (String texte : TEXTES_A_RISQUE) {
            PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(nommee(texte)));
        }
    }
}
