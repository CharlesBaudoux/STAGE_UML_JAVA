package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PlantUmlDiagrammeTest {

    private static final String LIGNE_MASQUAGE = "hide empty members";
    private static final String LIGNE_GAUCHE_DROITE = "left to right direction";

    private static UmlEntity boiteVide() {
        return UmlEntity.builder("com.exemple.Client", "Client").build();
    }

    private static UmlEntity boitePeuplee() {
        return UmlEntity.builder("com.exemple.Compte", "Compte")
            .fields(List.of(new UmlField("solde", "double", "-", false))).build();
    }

    private static UmlDiagram reglages(String titre, UMLFlow sens, boolean masquer) {
        return new UmlDiagram(titre, sens, masquer);
    }


    @Test
    void titreDeclare_estEcritEnTete() {
        String source = PlantUmlAssert.documentPour(
            reglages("Architecture du module Facturation", UMLFlow.TOP_TO_BOTTOM, true),
            boiteVide());

        assertTrue(source.contains("title Architecture du module Facturation"), source);
    }

    @Test
    void sansTitre_aucuneLigneTitreNestEcrite() {
        assertFalse(PlantUmlAssert.documentPour(boiteVide()).contains("title"),
            PlantUmlAssert.documentPour(boiteVide()));
    }

    @Test
    void titre_estEchappe() {
        String source = PlantUmlAssert.documentPour(
            reglages("Vue <b>générale</b>", UMLFlow.TOP_TO_BOTTOM, true), boiteVide());

        assertFalse(source.contains("<b>"), source);
        assertTrue(source.contains("&#60;b&#62;"), source);
    }

    @Test
    void titre_precedeLesBoites() {
        String source =
            PlantUmlAssert.documentPour(reglages("Facturation", UMLFlow.TOP_TO_BOTTOM, true),
                boiteVide());

        assertTrue(source.indexOf("title Facturation") < source.indexOf("class \"Client\""),
            source);
    }


    @Test
    void sensGaucheADroite_estDemandeAPlantUml() {
        String source = PlantUmlAssert.documentPour(
            reglages("", UMLFlow.LEFT_TO_RIGHT, true), boiteVide());

        assertTrue(source.contains(LIGNE_GAUCHE_DROITE), source);
    }

    @Test
    void sensParDefaut_nEcritAucuneDirective() {
        String source = PlantUmlAssert.documentPour(boiteVide());

        assertFalse(source.contains(LIGNE_GAUCHE_DROITE), source);
        assertFalse(source.contains("top to bottom direction"), source);
    }


    @Test
    void parDefaut_lesCompartimentsVidesSontMasques() {
        assertTrue(PlantUmlAssert.documentPour(boiteVide()).contains(LIGNE_MASQUAGE));
    }

    @Test
    void masquageRefuse_laDirectiveDisparait() {
        String source = PlantUmlAssert.documentPour(
            reglages("", UMLFlow.TOP_TO_BOTTOM, false), boiteVide());

        assertFalse(source.contains(LIGNE_MASQUAGE), source);
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void leMasquage_neDependPasDeLApparence(UmlStyle style) {
        assertTrue(PlantUmlAssert.documentPour(style, UmlDiagram.defaults(), boiteVide())
            .contains(LIGNE_MASQUAGE));
        assertFalse(PlantUmlAssert
            .documentPour(style, reglages("", UMLFlow.TOP_TO_BOTTOM, false), boiteVide())
            .contains(LIGNE_MASQUAGE));
    }


    @Test
    void lesReglagesDEnsemble_neTouchentAAucuneBoite() {
        UmlEntity[] entites = {boiteVide(), boitePeuplee()};

        assertEquals(
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(entites)),
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(
                reglages("Un titre", UMLFlow.LEFT_TO_RIGHT, false), entites)));
    }

    @Test
    void toutesLesDirectives_sontAccepteesParPlantUml() {
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(
            reglages("Architecture « Facturation »", UMLFlow.LEFT_TO_RIGHT, false),
            boiteVide(), boitePeuplee()));
    }
}
