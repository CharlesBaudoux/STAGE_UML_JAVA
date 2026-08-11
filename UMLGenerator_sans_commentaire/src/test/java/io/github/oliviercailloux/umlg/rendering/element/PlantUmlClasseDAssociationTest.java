package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.model.UmlAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantUmlClasseDAssociationTest {

    private static UmlEntity boite(String nom) {
        return UmlEntity.builder("com.exemple." + nom, nom).build();
    }

    private static UmlEntity inscription() {
        return UmlEntity.builder("com.exemple.Inscription", "Inscription")
            .relations(List.of(new UmlAssociationClass("com.exemple.Etudiant",
                "com.exemple.Cours")))
            .build();
    }

    private static String document() {
        return PlantUmlAssert.documentPour(boite("Etudiant"), boite("Cours"), inscription());
    }

    @Test
    void laLigne_relieLesDeuxExtremitesALaClasse() {
        String ligne = PlantUmlAssert.lignesDeLien(document()).get(0);

        assertTrue(ligne.matches("\\(\\S+, \\S+\\) \\.\\. \\S+"),
            "Attendu « (source, cible) .. porteur », lu : " + ligne);
    }

    @Test
    void lesTroisBoites_sontDesignees() {
        String document = document();
        String ligne = PlantUmlAssert.lignesDeLien(document).get(0);

        for (String alias : PlantUmlAssert.aliasDeclares(document)) {
            assertTrue(ligne.contains(alias), "L'alias " + alias + " manque à : " + ligne);
        }
    }

    @Test
    void uneSeuleLigne_estEcrite() {
        assertEquals(1, PlantUmlAssert.lignesDeLien(document()).size());
    }

    @Test
    void laSource_doitAvoirEteDeclaree() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PlantUmlAssert.documentPour(boite("Cours"), inscription()));

        assertTrue(e.getMessage().contains("Etudiant"), e.getMessage());
    }

    @Test
    void laCible_doitAvoirEteDeclaree() {
        assertThrows(IllegalArgumentException.class,
            () -> PlantUmlAssert.documentPour(boite("Etudiant"), inscription()));
    }

    @Test
    void leMoteurAcceptLaSyntaxe() {
        PlantUmlAssert.assertCompile(document());
    }
}
