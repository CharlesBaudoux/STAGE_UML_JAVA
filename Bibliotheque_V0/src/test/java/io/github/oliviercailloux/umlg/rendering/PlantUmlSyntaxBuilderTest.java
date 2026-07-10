package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import org.junit.jupiter.api.Test;

class PlantUmlSyntaxBuilderTest {

    // 1. Arrange : Création d'un cobaye avec toutes les nouvelles options du PDF
    @UMLClass(
        name = "ClientTest", 
        stereotype = "Entity", 
        color = "#FF0000", 
        strategy = UMLVisibility.PRIVATE
    )
    static class ClasseCobayeComplete {
        private String nom;
        public int age;
        
        public void faireQuelqueChose() {}
        private void methodeSecrete() {}
    }

    @Test
    void testSyntaxeEnteteEtPiedDePage() {
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder();
        
        // Act
        builder.startDocument();
        builder.endDocument();
        
        String resultat = builder.build();

        // Assert
        assertTrue(resultat.contains("@startuml"), "Le document doit commencer par @startuml");
        assertTrue(resultat.contains("skinparam classAttributeIconSize 0"), "Le document doit forcer l'affichage des symboles textes (+, -)");
        assertTrue(resultat.contains("@enduml"), "Le document doit se terminer par @enduml");
    }

    @Test
    void testSyntaxeClasseComplete() {
        // Arrange : Notre entité décode les règles grâce à l'API de Réflexion
        UmlEntity entite = EntityExtractor.extract(ClasseCobayeComplete.class);
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder();
        
        // Act : On génère le texte PlantUML
        builder.addClass(entite);
        String resultat = builder.build();

        // Assert : Vérification de la ligne de déclaration
        assertTrue(resultat.contains("class \"ClientTest\" as ClientTest <<Entity>> #FF0000 {"), 
            "La déclaration de la classe doit inclure le nom surchargé, le stéréotype et la couleur.");
        
        // Assert : Vérification des attributs (avec la stratégie PRIVATE)
        assertTrue(resultat.contains("- String nom"), "L'attribut privé doit être présent avec le symbole '-'");
        assertTrue(resultat.contains("+ int age"), "L'attribut public doit être présent avec le symbole '+'");
        
        // Assert : Vérification des méthodes
        assertTrue(resultat.contains("+ void faireQuelqueChose()"), "La méthode publique doit être formatée correctement");
        assertTrue(resultat.contains("- void methodeSecrete()"), "La méthode privée doit être formatée correctement");
    }
}