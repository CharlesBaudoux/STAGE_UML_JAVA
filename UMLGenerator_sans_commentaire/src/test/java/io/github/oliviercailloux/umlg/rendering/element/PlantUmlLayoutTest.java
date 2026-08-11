package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlLayout;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlantUmlLayoutTest {

    private static UmlEntity cible() {
        return UmlEntity.builder("com.exemple.Moteur", "Moteur").build();
    }

    private static UmlEntity source(UMLDirection direction) {
        return UmlEntity.builder("com.exemple.Voiture", "Voiture")
            .relations(List.of(new UmlLayout("com.exemple.Moteur", direction)))
            .build();
    }

    private static String ligneUnique(UMLDirection direction) {
        return PlantUmlAssert.lignesDeLien(
            PlantUmlAssert.documentPour(source(direction), cible())).get(0);
    }

    @ParameterizedTest
    @CsvSource({"UP,up", "DOWN,down", "LEFT,left", "RIGHT,right"})
    void directionDemandee_entreDansLeConnecteur(UMLDirection direction, String attendu) {
        assertTrue(ligneUnique(direction).contains("-[hidden]" + attendu + "->"),
            ligneUnique(direction));
    }

    @Test
    void sansDirection_leLienResteNu() {
        assertTrue(ligneUnique(UMLDirection.NONE).contains("-[hidden]->"),
            ligneUnique(UMLDirection.NONE));
    }

    @Test
    void leLien_nePorteAucuneEtiquette() {
        assertEquals(-1, ligneUnique(UMLDirection.DOWN).indexOf(" : "),
            ligneUnique(UMLDirection.DOWN));
    }

    @Test
    void laCible_doitAvoirEteDeclaree() {
        assertThrows(IllegalArgumentException.class,
            () -> PlantUmlAssert.documentPour(source(UMLDirection.DOWN)));
    }

    @Test
    void lePlacement_neChangeAucuneBoite() {
        assertEquals(
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(
                UmlEntity.builder("com.exemple.Voiture", "Voiture").build(), cible())),
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(
                source(UMLDirection.DOWN), cible())));
    }

    @Test
    void leMoteurAccepteLaSyntaxe() {
        PlantUmlAssert.assertCompile(
            PlantUmlAssert.documentPour(source(UMLDirection.DOWN), cible()));
    }
}
