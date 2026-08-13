package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantUmlSyntaxStructureTest {

    private static UmlEntity entite(List<UmlField> champs, List<UmlMethod> methodes) {
        return new UmlEntity("E", "", "", UMLVisibility.PUBLIC, champs, methodes);
    }

    private static final UmlField CHAMP = new UmlField("nom", "String", "-", false);
    private static final UmlMethod METHODE = new UmlMethod("agir", "void", "+", false, List.of());

    private static String rendu(UmlEntity entity) {
        return PlantUmlAssert.documentPour(entity);
    }


    @Test
    void separateur_presentUniquementSiChampsEtMethodes() {
        String source = rendu(entite(List.of(CHAMP), List.of(METHODE)));

        assertTrue(source.contains("  --\n"),
            "Le séparateur doit apparaître entre attributs et méthodes.");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void separateur_absentSiSeulementDesChamps() {
        String source = rendu(entite(List.of(CHAMP), List.of()));

        assertFalse(source.contains("--"),
            "Sans méthode, un séparateur laisserait un compartiment vide dans le rendu.");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void separateur_absentSiSeulementDesMethodes() {
        String source = rendu(entite(List.of(), List.of(METHODE)));

        assertFalse(source.contains("--"));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void entiteSansAucunMembre_produitUneCoquilleValide() {
        String source = rendu(entite(List.of(), List.of()));

        assertFalse(source.contains("--"));
        PlantUmlAssert.assertCompile(source);
    }


    @Test
    void champ_estRenduTypePuisNom() {
        String source = rendu(entite(List.of(new UmlField("age", "int", "+", false)), List.of()));

        assertTrue(source.contains("  + age : int\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void champStatique_porteLeMarqueurStatic() {
        String source = rendu(entite(List.of(new UmlField("SEUIL", "int", "+", true)), List.of()));

        assertTrue(source.contains("  + {static} SEUIL : int\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void lesQuatreSymbolesDeVisibilite_sontRendusTelsQuels() {
        String source = rendu(entite(List.of(
            new UmlField("a", "int", "+", false),
            new UmlField("b", "int", "-", false),
            new UmlField("c", "int", "#", false),
            new UmlField("d", "int", "~", false)), List.of()));

        assertTrue(source.contains("+ a : int"), source);
        assertTrue(source.contains("- b : int"), source);
        assertTrue(source.contains("# c : int"), source);
        assertTrue(source.contains("~ d : int"), source);
        PlantUmlAssert.assertCompile(source);
    }


    @Test
    void methodeSansParametre_renduAvecParenthesesVides() {
        String source = rendu(entite(List.of(), List.of(METHODE)));

        assertTrue(source.contains("  + agir() : void\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void parametres_sontJointsParVirguleEspace() {
        UmlMethod m = new UmlMethod("calculer", "double", "+", false,
            List.of("int", "String", "boolean"));
        String source = rendu(entite(List.of(), List.of(m)));

        assertTrue(source.contains("  + calculer(int, String, boolean) : double\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void methodeStatique_porteLeMarqueurStaticAvantLeTypeDeRetour() {
        UmlMethod m = new UmlMethod("creer", "E", "+", true, List.of());
        String source = rendu(entite(List.of(), List.of(m)));

        assertTrue(source.contains("  + {static} creer() : E\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void typeGenerique_neCassePasLaSyntaxe() {
        UmlField champ = new UmlField("elements", "String[]", "-", false);
        String source = rendu(entite(List.of(champ), List.of()));

        assertTrue(source.contains("- elements : String[]"), source);
        PlantUmlAssert.assertCompile(source);
    }


    @Test
    void stereotypeVide_nEstPasRendu() {
        String source =
            rendu(new UmlEntity("E", "", "", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertFalse(source.contains("<<"), "Un stéréotype vide ne doit pas produire « <<>> ».");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void stereotypeRenseigne_estRenduEntreChevrons() {
        String source =
            rendu(new UmlEntity("E", "Service", "", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertTrue(source.contains("<<Service>>"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void couleurSansDiese_estNormaliseeCommeAvecDiese() {
        String sans =
            rendu(new UmlEntity("E", "", "AABBCC", UMLVisibility.PUBLIC, List.of(), List.of()));
        String avec =
            rendu(new UmlEntity("E", "", "#AABBCC", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertEquals(avec, sans, "« AABBCC » et « #AABBCC » doivent produire un rendu identique.");
        assertTrue(sans.contains("#AABBCC"), sans);
        PlantUmlAssert.assertCompile(sans);
    }

    @Test
    void couleurVide_nEstPasRendue() {
        String source =
            rendu(new UmlEntity("E", "", "", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertTrue(source.contains("class \"E\" as E {"),
            () -> "Une couleur vide ne doit pas produire un « # » orphelin :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void stereotypeEtCouleur_coexistentDansLaDeclaration() {
        String source = rendu(
            new UmlEntity("E", "Entity", "#FF0000", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertTrue(source.contains("class \"E\" as E <<Entity>> #FF0000 {"), source);
        PlantUmlAssert.assertCompile(source);
    }


    @Test
    void documentComplet_ouvreEtFermeLeDiagramme() {
        String source = rendu(entite(List.of(), List.of()));

        assertTrue(source.startsWith("@startuml\n"), source);
        assertTrue(source.endsWith("@enduml\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void plusieursClasses_sontToutesDeclarees() {
        UmlEntity a = new UmlEntity("A", "", "", UMLVisibility.PUBLIC, List.of(), List.of());
        UmlEntity b = new UmlEntity("B", "", "", UMLVisibility.PUBLIC, List.of(), List.of());

        String source = PlantUmlAssert.documentPour(a, b);

        assertEquals(List.of("A", "B"), PlantUmlAssert.nomsDeclares(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void addClass_estFluent() {
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder(UmlStyle.PLANTUML);
        UmlEntity e = entite(List.of(), List.of());

        assertSame(builder, builder.addClass(e),
            "addClass déclare renvoyer this : le chaînage doit fonctionner.");
    }

    @Test
    void rendu_estDeterministe() {
        UmlEntity e = entite(List.of(CHAMP), List.of(METHODE));

        assertEquals(rendu(e), rendu(e), "Deux rendus de la même entité doivent être identiques.");
    }
}
