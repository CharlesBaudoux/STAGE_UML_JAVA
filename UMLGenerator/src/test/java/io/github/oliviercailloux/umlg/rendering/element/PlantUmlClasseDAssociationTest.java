package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.model.UmlAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// La classe d'association : « (Etudiant, Cours) .. Inscription ».
//
// La seule relation à trois boîtes, et la seule dont la ligne ne commence pas par la classe qui
// la déclare.
class PlantUmlClasseDAssociationTest {

    private static UmlEntity boite(String nom) {
        return UmlEntity.builder("com.exemple." + nom, nom).build();
    }

    // Inscription qualifie le lien entre Etudiant et Cours.
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
        // Les alias sont ceux du registre, pas les noms : on vérifie donc qu'ils sont bien trois
        // et distincts, et que la ligne les emploie tous.
        String document = document();
        String ligne = PlantUmlAssert.lignesDeLien(document).get(0);

        for (String alias : PlantUmlAssert.aliasDeclares(document)) {
            assertTrue(ligne.contains(alias), "L'alias " + alias + " manque à : " + ligne);
        }
    }

    @Test
    void uneSeuleLigne_estEcrite() {
        // Le trait entre les deux extrémités est dessiné par cette ligne même : en écrire une
        // seconde doublerait le lien.
        assertEquals(1, PlantUmlAssert.lignesDeLien(document()).size());
    }

    @Test
    void laSource_doitAvoirEteDeclaree() {
        // La seconde extrémité passe par le même registre que la première : une classe absente
        // du diagramme est signalée, et non silencieusement recréée par PlantUML.
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
