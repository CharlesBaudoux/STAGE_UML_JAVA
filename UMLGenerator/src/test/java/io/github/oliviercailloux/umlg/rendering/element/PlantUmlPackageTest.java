package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// Le regroupement visuel : quelles boîtes entrent dans quel rectangle, et ce que le rectangle
// ne change pas.
class PlantUmlPackageTest {

    private static UmlEntity boite(String nom, UmlPackage groupe) {
        return UmlEntity.builder("com.exemple." + nom, nom).group(groupe).build();
    }

    private static UmlEntity boite(String nom) {
        return boite(nom, UmlPackage.none());
    }

    // Les lignes du document, débarrassées de leur indentation : le contenu d'un rectangle est
    // écrit décalé, et ce décalage ne doit pas entrer dans les comparaisons.
    private static List<String> lignes(String source) {
        return source.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    @Test
    void classeGroupee_estEncadreeParUnRectangle() {
        String source = PlantUmlAssert.documentPour(
            boite("Facture", new UmlPackage("Module Paiement", "")));

        assertTrue(source.contains("package \"Module Paiement\" {"), source);
        // Le rectangle doit se refermer : sans l'accolade, PlantUML avalerait tout ce qui suit.
        assertTrue(lignes(source).contains("}"), source);
    }

    @Test
    void groupeColore_porteSaCouleur() {
        String source = PlantUmlAssert.documentPour(
            boite("Facture", new UmlPackage("Module Paiement", "#EEEEEE")));

        assertTrue(source.contains("package \"Module Paiement\" #EEEEEE {"), source);
    }

    @Test
    void classeSansGroupe_nEstEncadreeParRien() {
        assertFalse(PlantUmlAssert.documentPour(boite("Facture")).contains("package "),
            "Une classe sans @UMLPackage se dessine au premier niveau.");
    }

    @Test
    void deuxClassesDuMemeGroupe_partagentUnSeulRectangle() {
        UmlPackage groupe = new UmlPackage("Facturation", "");
        String source = PlantUmlAssert.documentPour(
            boite("Facture", groupe), boite("Ligne", groupe));

        // Une seule ouverture pour les deux : c'est le nom qui fait l'identité du groupe.
        assertEquals(1, source.lines().filter(l -> l.startsWith("package ")).count(), source);
        assertTrue(source.indexOf("Facture") > source.indexOf("package "), source);
    }

    @Test
    void deuxGroupesDistincts_donnentDeuxRectangles() {
        String source = PlantUmlAssert.documentPour(
            boite("Facture", new UmlPackage("Facturation", "")),
            boite("Compte", new UmlPackage("Sécurité", "")));

        assertEquals(2, source.lines().filter(l -> l.startsWith("package ")).count(), source);
    }

    @Test
    void classesGroupeesEtLibres_cohabitent() {
        String source = PlantUmlAssert.documentPour(
            boite("Libre"), boite("Rangee", new UmlPackage("Facturation", "")));

        // Les boîtes sans groupe viennent en tête : elles n'ont pas de rectangle à ouvrir, et la
        // source se lit mieux ainsi.
        assertTrue(source.indexOf("\"Libre\"") < source.indexOf("package "), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void couleursContradictoires_laPremiereLEmporte() {
        // Un rectangle n'a qu'une couleur. La première rencontrée est retenue, et l'écart est
        // signalé sur la sortie d'erreur — ce que ce test ne mesure pas, mais qui explique
        // pourquoi la seconde disparaît sans que rien n'échoue.
        String source = PlantUmlAssert.documentPour(
            boite("Facture", new UmlPackage("Facturation", "#EEEEEE")),
            boite("Ligne", new UmlPackage("Facturation", "#123456")));

        assertTrue(source.contains("#EEEEEE"), source);
        assertFalse(source.contains("#123456"), source);
    }

    @Test
    void couleurDeclareeApresUnGroupeSansCouleur_estRetenue() {
        // Rien ne s'y oppose : aucune couleur n'avait encore été choisie, et la perdre serait
        // gratuit.
        String source = PlantUmlAssert.documentPour(
            boite("Facture", new UmlPackage("Facturation", "")),
            boite("Ligne", new UmlPackage("Facturation", "#123456")));

        assertTrue(source.contains("package \"Facturation\" #123456 {"), source);
    }

    // Ce que la bibliothèque a écrit sur la sortie d'erreur pendant le rendu.
    //
    // Le flux est remplacé le temps de l'appel, puis rendu : un test qui laisserait derrière lui
    // un System.err détourné fausserait tous les suivants, d'où le try/finally.
    private static String erreurPendantLeRendu(UmlEntity... entites) {
        java.io.PrintStream original = System.err;
        java.io.ByteArrayOutputStream capture = new java.io.ByteArrayOutputStream();
        try {
            System.setErr(new java.io.PrintStream(capture, true,
                java.nio.charset.StandardCharsets.UTF_8));
            PlantUmlAssert.documentPour(entites);
        } finally {
            System.setErr(original);
        }
        return capture.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void couleurAjouteeAUnGroupeQuiNenAvaitPas_neSignaleRien() {
        // Un avertissement qui annonce la perte d'une couleur effectivement dessinée serait pire
        // que pas d'avertissement du tout : il apprend à ne plus les lire.
        assertTrue(erreurPendantLeRendu(
            boite("Facture", new UmlPackage("Facturation", "")),
            boite("Ligne", new UmlPackage("Facturation", "#123456"))).isEmpty(),
            "Aucune couleur n'est perdue : il n'y a rien à signaler.");
    }

    @Test
    void couleurReellementPerdue_estSignalee() {
        String erreurs = erreurPendantLeRendu(
            boite("Facture", new UmlPackage("Facturation", "#EEEEEE")),
            boite("Ligne", new UmlPackage("Facturation", "#123456")));

        assertTrue(erreurs.contains("#123456"), erreurs);
        assertTrue(erreurs.contains("com.exemple.Ligne"), erreurs);
    }

    @Test
    void classeSansCouleurDansUnGroupeColore_neSignaleRien() {
        assertTrue(erreurPendantLeRendu(
            boite("Facture", new UmlPackage("Facturation", "#EEEEEE")),
            boite("Ligne", new UmlPackage("Facturation", ""))).isEmpty(),
            "Une classe qui ne demande aucune couleur n'en perd aucune.");
    }

    @Test
    void couleurInconnue_estRefusee() {
        // La même exigence que pour une boîte : le vocabulaire des couleurs est celui du moteur
        // de rendu, et il est fermé.
        assertThrows(IllegalArgumentException.class, () -> PlantUmlAssert.documentPour(
            boite("Facture", new UmlPackage("Facturation", "PasUneCouleur"))));
    }

    @Test
    void nomDuGroupe_estEchappe() {
        String source = PlantUmlAssert.documentPour(
            boite("Facture", new UmlPackage("Vue <b>large</b>", "")));

        assertFalse(source.contains("<b>"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void unLienTraverseLesRectangles() {
        // Une association entre deux groupes reste un trait ordinaire : les relations sont
        // écrites hors des rectangles, après toutes les boîtes.
        UmlEntity source = UmlEntity.builder("com.exemple.Facture", "Facture")
            .group(new UmlPackage("Facturation", ""))
            .associations(List.of(
                UmlAssociation.builder("compte", "com.exemple.Compte").build()))
            .build();
        UmlEntity cible = boite("Compte", new UmlPackage("Sécurité", ""));

        String document = PlantUmlAssert.documentPour(source, cible);

        assertEquals(1, PlantUmlAssert.lignesDeLien(document).size(), document);
        PlantUmlAssert.assertCompile(document);
    }

    @Test
    void leGroupe_neChangeAucuneBoite() {
        // Un rectangle range les boîtes ; il ne les modifie pas.
        assertEquals(
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(boite("Facture"))),
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(
                boite("Facture", new UmlPackage("Facturation", "#EEEEEE")))));
    }
}
