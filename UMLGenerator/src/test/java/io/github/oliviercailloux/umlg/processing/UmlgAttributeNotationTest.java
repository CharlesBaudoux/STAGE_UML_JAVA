package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgAttributeNotationTest {

    private static Verdict compiler(Path tmp, String code) throws IOException {
        return CompilationFixture.compileBody(tmp, code);
    }

    @Test
    void anAccessorInAttributeNotation_compilesWithoutAWord(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Keyboard {
                @UMLInclude(asAttribute = true)
                public Map<String, Short> codeByName() { return Map.of(); }
            }
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void aMethodWithParameters_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Keyboard {
                @UMLInclude(asAttribute = true)
                public String canonical(String keyName) { return keyName; }
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("takes parameters"), verdict.report());
    }

    @Test
    void aMethodWithoutReturn_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Keyboard {
                @UMLInclude(asAttribute = true)
                public void validate() {}
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("returns nothing"), verdict.report());
    }

    @Test
    void onAField_theNotationIsRefused(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Keyboard {
                @UMLInclude(asAttribute = true)
                public String name;
            }
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("already an attribute"), verdict.report());
    }

    @Test
    void excludingAComponent_isNotWithoutEffect(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.NONE)
            record Point(@UMLExclude int x, int y) {}
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void decoratingAComponent_neverMakesItInvisible(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.NONE)
            @UMLConstraint(rule = "x >= 0")
            record Point(@UMLDerived @UMLMultiplicity("1") @UMLNote(text = "abscissa") int x) {}
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void includingAComponent_staysSilent(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.NONE)
            record Point(@UMLInclude int x, int y) {}
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }
}
