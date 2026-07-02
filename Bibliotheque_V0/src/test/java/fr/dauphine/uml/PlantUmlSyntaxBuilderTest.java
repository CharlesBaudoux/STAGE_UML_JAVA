package fr.dauphine.uml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlantUmlSyntaxBuilderTest {

    @Test
    void testCoquilleVide() {
        // 1. Préparation (Arrange)
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder();

        // 2. Exécution (Act)
        builder.startDocument()
               .endDocument();
        String resultat = builder.build();

        // 3. Vérification (Assert)
        assertEquals("@startuml\n\n@enduml\n", resultat, "Le document vide n'est pas formaté correctement.");
    }

    @Test
    void testClasseSansStereotype() {
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder();

        builder.startDocument()
               .addClass("Client", Optional.empty())
               .endClass()
               .endDocument();
               
        String resultat = builder.build();

        // On vérifie que le texte généré contient bien les bons éléments
        assertTrue(resultat.contains("class Client {"), "La déclaration de la classe est incorrecte.");
        assertTrue(resultat.contains("}"), "La fermeture de la classe est manquante.");
    }

    @Test
    void testClasseAvecStereotype() {
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder();

        builder.startDocument()
               .addClass("CompteBancaire", Optional.of("abstract"))
               .endClass()
               .endDocument();
               
        String resultat = builder.build();

        // On vérifie que le stéréotype a bien été formaté avec les chevrons << >>
        assertTrue(resultat.contains("class CompteBancaire <<abstract>> {"), "Le stéréotype n'est pas formaté correctement.");
    }
}