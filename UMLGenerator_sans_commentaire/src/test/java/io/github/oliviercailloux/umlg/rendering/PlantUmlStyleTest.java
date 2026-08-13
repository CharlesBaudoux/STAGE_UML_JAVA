package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PlantUmlStyleTest {

    private static final String GRIS_PLANTUML = "#F1F1F1";
    private static final String PASTILLE_CLASSE = "#ADD1B2";
    private static final String BANDEAU_UMLG = "#DDE7F0";

    private static UmlEntity boite() {
        return UmlEntity.builder("com.exemple.Client", "Client")
            .fields(List.of(new UmlField("solde", "double", "-", false))).build();
    }

    private static UmlEntity[] paire() {
        return new UmlEntity[] {
            UmlEntity.builder("com.exemple.Client", "Client")
                .associations(ImmutableList.of(UmlAssociation.builder("champ", "com.exemple.Cible")
                    .targetMultiplicity("0..*").label("détient").build()))
                .build(),
            UmlEntity.builder("com.exemple.Cible", "Cible").build()};
    }


    @Test
    void parDefaut_aucuneCouleurNestImposee() {
        String source = PlantUmlAssert.documentPour(boite());

        assertFalse(source.contains("ClassBackgroundColor"), source);
        assertFalse(source.contains("ClassHeaderBackgroundColor"), source);
        assertFalse(source.contains("ClassBorderColor"), source);
        assertFalse(source.contains("ArrowColor"), source);
    }

    @Test
    void parDefaut_lapastilleNestPasMasquee() {
        assertFalse(PlantUmlAssert.documentPour(boite()).contains("hide circle"));
    }

    @Test
    void themeUmlg_imposeSesCouleursEtMasqueLaPastille() {
        String source = PlantUmlAssert.documentPour(UmlStyle.UMLG, boite());

        assertTrue(source.contains("ClassHeaderBackgroundColor " + BANDEAU_UMLG), source);
        assertTrue(source.contains("hide circle"), source);
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void touteApparence_conserveCeQuiTientAuSens(UmlStyle style) {
        String source = PlantUmlAssert.documentPour(style, boite());

        assertTrue(source.contains("skinparam classAttributeIconSize 0"), source);
        assertTrue(source.contains("hide empty members"), source);
        assertTrue(source.contains("skinparam nodesep "), source);
        assertTrue(source.contains("skinparam ranksep "), source);
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void touteApparence_estAccepteeParPlantUml(UmlStyle style) {
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(style, paire()));
    }


    @Test
    void parDefaut_leRenduEstCeluiDePlantUml() throws Exception {
        String svg = PlantUmlAssert.svg(boite());

        assertTrue(svg.contains(GRIS_PLANTUML),
            () -> "Le fond gris de PlantUML est attendu :\n" + svg);
        assertTrue(svg.contains(PASTILLE_CLASSE),
            () -> "La pastille « C » verte est attendue :\n" + svg);
    }

    @Test
    void themeUmlg_remplaceLeRenduDePlantUml() throws Exception {
        String svg = PlantUmlAssert.svg(UmlStyle.UMLG, boite());

        assertTrue(svg.contains(BANDEAU_UMLG), () -> "Le bandeau bleu est attendu :\n" + svg);
        assertFalse(svg.contains(PASTILLE_CLASSE),
            () -> "La pastille doit être masquée sous ce thème :\n" + svg);
    }


    @Test
    void nomDeLien_estColoreParLeThemeSeulement() {
        assertFalse(PlantUmlAssert.documentPour(paire()).contains("<color:"),
            "L'apparence par défaut ne colore rien.");
        assertTrue(PlantUmlAssert.documentPour(UmlStyle.UMLG, paire()).contains("<color:"),
            "Le thème de la bibliothèque colore le nom des liens.");
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void nomDeLien_resteEnItaliqueDansLesDeuxCas(UmlStyle style) {
        assertTrue(PlantUmlAssert.documentPour(style, paire()).contains("<i>détient</i>"));
    }


    @Test
    void lesDeuxApparences_disentExactementLaMemeChose() {
        assertEquals(PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(paire())),
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(UmlStyle.UMLG, paire())));

        assertEquals(PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(paire())),
            PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(UmlStyle.UMLG, paire())));
    }

    @Test
    void lesDeuxApparences_neProduisentPasLeMemeDocument() {
        assertNotEquals(PlantUmlAssert.documentPour(paire()),
            PlantUmlAssert.documentPour(UmlStyle.UMLG, paire()));
    }
}
