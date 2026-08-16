package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The header of a box — name, stereotype — seen from the processor, and the question of whether it
// judge as the generation.
//
// The processor asked the question with isBlank(), which does not treat control characters
// as blanks; the generation, for its part, puts it on the escaped text, where those characters are gone.
// The same code therefore got two answers: "name = \"\1\"" compiled, then failed at drawing time
// on "class \"\" as C", which the engine refuses. The rule now lives in
// ClassHeaderValidator, and both halves read it in the same place.
class UmlgClassHeaderTest {

    private static final String Header = "import io.github.oliviercailloux.umlg.annotation.*;\n";

    private static Verdict compiler(Path tmp, String code) throws IOException {
        return CompilationFixture.compiler(tmp, "Fixture.java", Header + code);
    }

    @Test
    void nameMadeOfInvisibleCharacters_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(name = "\\1\\2")
            class Client {}
            """);

        assertFalse(verdict.successful(), "A box with an invisible name is not drawn.");
        assertTrue(verdict.errorContains("name() must show something"), verdict.report());
    }

    @Test
    void stereotypeMadeOfInvisibleCharacters_stopsCompilation(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(stereotype = "\\1\\2")
            class Client {}
            """);

        assertFalse(verdict.successful(), "\"<<>>\" is refused by the drawing engine.");
        assertTrue(verdict.errorContains("stereotype() must show something"), verdict.report());
    }

    @Test
    void anOrdinaryNameWithAControlCharacter_compiles(@TempDir Path tmp) throws IOException {
        // The rule does not harden: it is enough that something is left to draw.
        Verdict verdict = compiler(tmp, """
            @UMLClass(name = "Account\\1", stereotype = "entity\\1")
            class Client {}
            """);

        assertTrue(verdict.successful(), verdict.report());
    }

    @Test
    void anUnwrittenName_isNotComplainedAbout(@TempDir Path tmp) throws IOException {
        // The empty string is the default of name(): it means "the name of the class", and not
        // "a box without a name". That is the only difference between the two halves, and it is
        // in the caller, not in the rule.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {}
            """);

        assertTrue(verdict.successful(), verdict.report());
    }
}
