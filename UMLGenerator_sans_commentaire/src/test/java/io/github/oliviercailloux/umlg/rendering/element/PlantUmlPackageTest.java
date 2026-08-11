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

class PlantUmlPackageTest {

    private static UmlEntity boite(String nom, UmlPackage groupe) {
        return UmlEntity.builder("com.exemple." + nom, nom).group(groupe).build();
    }

    private static UmlEntity boite(String nom) {
        return boite(nom, UmlPackage.aucun());
    }

    private static List<String> lignes(String source) {
        return source.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    @Test
    void classeGroupee_estEncadreeParUnRectangle() {
        String source = PlantUmlAssert.documentPour(
            boite("Facture", new UmlPackage("Module Paiement", "")));

        assertTrue(source.contains("package \"Module Paiement\" {"), source);
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

        assertTrue(source.indexOf("\"Libre\"") < source.indexOf("package "), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void couleursContradictoires_laPremiereLEmporte() {
        String source = PlantUmlAssert.documentPour(
            boite("Facture", new UmlPackage("Facturation", "#EEEEEE")),
            boite("Ligne", new UmlPackage("Facturation", "#123456")));

        assertTrue(source.contains("#EEEEEE"), source);
        assertFalse(source.contains("#123456"), source);
    }

    @Test
    void couleurDeclareeApresUnGroupeSansCouleur_estRetenue() {
        String source = PlantUmlAssert.documentPour(
            boite("Facture", new UmlPackage("Facturation", "")),
            boite("Ligne", new UmlPackage("Facturation", "#123456")));

        assertTrue(source.contains("package \"Facturation\" #123456 {"), source);
    }

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
        assertEquals(
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(boite("Facture"))),
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(
                boite("Facture", new UmlPackage("Facturation", "#EEEEEE")))));
    }
}
