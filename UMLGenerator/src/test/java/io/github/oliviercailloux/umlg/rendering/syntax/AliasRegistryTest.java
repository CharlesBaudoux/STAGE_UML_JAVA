package io.github.oliviercailloux.umlg.rendering.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import io.github.oliviercailloux.umlg.rendering.PlantUmlWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

// Alias making, exercised on the names a naive cleanup would break:
// ideograms, punctuation only, purely numeric name, spaces, accents.
//
// The danger aimed at is silence. PlantUML complains neither of an empty alias nor of two classes
// sharing an alias: in the first case it returns an error image, in the second it
// merges two boxes — while the program announces "Success". Hence the call to
// assertCompile in each test.
class AliasRegistryTest {

    @Test
    void twoHomonyms_receiveDistinctAliases() {
        // The aliases do not step on each other — that is what guarantees each stroke lands on
        // the right box. But the alias is not read on the drawing: that is why
        // registration warns on top, both boxes being visually identical.
        AliasRegistry registry = new AliasRegistry();

        String first = registry.register("a.Client", "Client");
        String second = registry.register("b.Client", "Client");

        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
    }

    // Two names which, once cleaned, both give "MyClient".
    @UMLClass(name = "Mon.Client")
    static class NameWithPoint {}

    @UMLClass(name = "MonClient")
    static class NameWithoutPoint {}

    @UMLClass(name = "MonClient")
    static class NameWithoutPointBis {}

    @UMLClass(name = "客户")
    static class NameIdeograms {}

    @UMLClass(name = "Network")
    static class NameAccented {}

    @UMLClass(name = "...")
    static class NamePunctuationOnly {}

    @UMLClass(name = "42")
    static class NumericName {}

    @UMLClass(name = "Service Payment Handler")
    static class NameWithSpaces {}

    // --- An alias must always be a usable PlantUML identifier ---

    @Test
    void nameInIdeograms_producesAUsableAlias() {
        // "客户" holds no [a-zA-Z0-9] character: the naive cleanup reduced it to
        // l'alias empty, PlantUML recevait « clazz "客户" as  { » and rendait one image d'error,
        // while PlantUmlWriter displayed "Success".
        String source = PlantUmlAssert.documentFor(EntityExtractor.extract(NameIdeograms.class));

        assertEquals(List.of("客户"), PlantUmlAssert.namesDeclared(source),
            "The displayed name must stay the original unicode.");
        assertAliasNotEmpty(source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void nameOfPunctuationOnly_producesAUsableAlias() {
        String source =
            PlantUmlAssert.documentFor(EntityExtractor.extract(NamePunctuationOnly.class));

        assertAliasNotEmpty(source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void numericName_producesAnAliasNotStartingWithADigit() {
        String source = PlantUmlAssert.documentFor(EntityExtractor.extract(NumericName.class));

        String alias = PlantUmlAssert.aliasDeclared(source).get(0);
        assertTrue(Character.isLetter(alias.charAt(0)),
            "An alias starting with a digit is not a safe identifier: " + alias);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void nameWithSpaces_producesAnAliasWithoutSpace() {
        // A space in the alias would cut the declaration in two for the PlantUML parser.
        String source = PlantUmlAssert.documentFor(EntityExtractor.extract(NameWithSpaces.class));

        String alias = PlantUmlAssert.aliasDeclared(source).get(0);
        assertEquals("ServicePaymentHandler", alias);
        assertEquals(List.of("Service Payment Handler"), PlantUmlAssert.namesDeclared(source),
            "The displayed name must keep its spaces.");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void accentedName_keepsItsAsciiLettersInTheAlias() {
        String source = PlantUmlAssert.documentFor(EntityExtractor.extract(NameAccented.class));

        assertAliasNotEmpty(source);
        assertEquals(List.of("Network"), PlantUmlAssert.namesDeclared(source));
        PlantUmlAssert.assertCompile(source);
    }

    // --- Two distinct entities must never share an alias ---

    @Test
    void twoDifferentNamesReducingToTheSameAlias_stayTwoDistinctClasses() {
        // "My.Client" and "MyClient" both clean up to "MyClient". PlantUML does not
        // reports no error in that case: it takes the second declaration as the
        // the same class and merges the two boxes. The diagram silently loses a class.
        String source = PlantUmlAssert.documentFor(
            EntityExtractor.extract(NameWithPoint.class),
            EntityExtractor.extract(NameWithoutPoint.class));

        List<String> alias = PlantUmlAssert.aliasDeclared(source);

        assertEquals(2, alias.size(), "Both classes must be declared.");
        assertNotEquals(alias.get(0), alias.get(1),
            "Two distinct entities sharing an alias are silently merged by "
                + "PlantUML.");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void sameDisplayedNameForTwoEntities_staysTwoDistinctClasses() {
        // Real case: two homonymous classes in two different packages, both
        // annotated. They must appear as two boxes.
        UmlEntity a = EntityExtractor.extract(NameWithoutPoint.class);
        UmlEntity b = EntityExtractor.extract(NameWithoutPointBis.class);

        String source = PlantUmlAssert.documentFor(a, b);
        List<String> alias = PlantUmlAssert.aliasDeclared(source);

        assertEquals(2, alias.size());
        assertNotEquals(alias.get(0), alias.get(1),
            "Two entities rendered separately must receive two distinct aliases.");
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void aliasOfAnAlreadyCleanName_staysTheNameItself() {
        // Non-regression guard: the ordinary case must not be disfigured by the
        // the hardening above (no stray suffix or prefix).
        String source = PlantUmlAssert.documentFor(EntityExtractor.extract(NameWithoutPoint.class));

        assertEquals(List.of("MonClient"), PlantUmlAssert.aliasDeclared(source));
        PlantUmlAssert.assertCompile(source);
    }

    // Shared assertion: a single declared class, and its alias is not empty.
    private static void assertAliasNotEmpty(String source) {
        List<String> alias = PlantUmlAssert.aliasDeclared(source);
        assertEquals(1, alias.size(), "One only class expected in :\n" + source);
        assertFalse(alias.get(0).isBlank(),
            "An empty alias breaks the PlantUML declaration:\n" + source);
    }
}
