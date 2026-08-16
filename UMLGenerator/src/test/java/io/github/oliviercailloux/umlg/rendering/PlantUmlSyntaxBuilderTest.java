package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import org.junit.jupiter.api.Test;

// Two overall checks on the assembly of the document: the skeleton (@startuml, style,
// @enduml) and a complete box, from the annotations to the generated text.
//
// The detail of each chunk is exercised elsewhere (PlantUmlSyntaxStructureTest); this file
// checks that everything holds together.
class PlantUmlSyntaxBuilderTest {

    // 1. Arrange: build a fixture with every new option of the PDF
    @UMLClass(
        name = "ClientTest",
        stereotype = "Entity",
        color = "#FF0000",
        strategy = UMLVisibility.PRIVATE
    )
    static class CompleteFixtureClass {
        private String name;
        public int age;

        public void doSomeThing() {}
        private void methodSecret() {}
    }

    @Test
    void testSyntaxHeaderAndFooter() {
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder(UmlStyle.PLANTUML);

        // Act
        builder.startDocument();
        builder.endDocument();

        String result = builder.build();

        // Assert
        assertTrue(result.contains("@startuml"), "The document must start with @startuml");
        assertTrue(result.contains("skinparam classAttributeIconSize 0"),
            "The document must force the display of the text symbols (+, -)");
        assertTrue(result.contains("@enduml"), "The document must end with @enduml");
    }

    @Test
    void testSyntaxCompleteClass() {
        // Arrange: our entity decodes the rules through the reflection API
        UmlEntity entity = EntityExtractor.extract(CompleteFixtureClass.class);
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder(UmlStyle.PLANTUML);

        // Act: generate the PlantUML text
        builder.addClass(entity);
        String result = builder.build();

        // Assert: check the declaration line
        assertTrue(result.contains("class \"ClientTest\" as ClientTest <<Entity>> #FF0000 {"),
            "The class declaration must include the overridden name, the stereotype and the "
                + "color.");

        // Assert: check the fields (with the PRIVATE strategy)
        assertTrue(result.contains("- name : String"),
            "The private field must be present with the '-' symbol");
        assertTrue(result.contains("+ age : int"),
            "The public field must be present with the '+' symbol");

        // Assert: check the methods
        assertTrue(result.contains("+ doSomeThing() : void"),
            "The public method must be formatted correctly");
        assertTrue(result.contains("- methodSecret() : void"),
            "The private method must be formatted correctly");
    }
}