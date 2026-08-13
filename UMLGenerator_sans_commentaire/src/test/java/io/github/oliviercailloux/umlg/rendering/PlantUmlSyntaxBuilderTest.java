package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import org.junit.jupiter.api.Test;

class PlantUmlSyntaxBuilderTest {

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
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder(UmlStyle.PLANTUML);

        builder.startDocument();
        builder.endDocument();

        String resultat = builder.build();

        assertTrue(resultat.contains("@startuml"), "Le document doit commencer par @startuml");
        assertTrue(resultat.contains("skinparam classAttributeIconSize 0"),
            "Le document doit forcer l'affichage des symboles textes (+, -)");
        assertTrue(resultat.contains("@enduml"), "Le document doit se terminer par @enduml");
    }

    @Test
    void testSyntaxeClasseComplete() {
        UmlEntity entite = EntityExtractor.extract(ClasseCobayeComplete.class);
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder(UmlStyle.PLANTUML);

        builder.addClass(entite);
        String resultat = builder.build();

        assertTrue(resultat.contains("class \"ClientTest\" as ClientTest <<Entity>> #FF0000 {"),
            "La déclaration de la classe doit inclure le nom surchargé, le stéréotype et la "
                + "couleur.");

        assertTrue(resultat.contains("- nom : String"),
            "L'attribut privé doit être présent avec le symbole '-'");
        assertTrue(resultat.contains("+ age : int"),
            "L'attribut public doit être présent avec le symbole '+'");

        assertTrue(resultat.contains("+ faireQuelqueChose() : void"),
            "La méthode publique doit être formatée correctement");
        assertTrue(resultat.contains("- methodeSecrete() : void"),
            "La méthode privée doit être formatée correctement");
    }
}