package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The attribute notation — @UMLInclude(asAttribute = true) — seen from the processor, and the fate of the
// record components in the "without effect" warnings.
//
// The notation requires a method without parameter returning something: the parentheses
// removed, only the name and the type are left, and both must exist. The three
// gaps are errors — the generation would refuse them just the same, the processor says so at
// compilation, that is its whole point.
class UmlgAttributeNotationTest {

    // The header every fixture shares lives in CompilationFixture, with the harness.
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

    // --- Record components, in the "without effect" warnings ---

    @Test
    void excludingAComponent_isNotWithoutEffect(@TempDir Path tmp) throws IOException {
        // Under strategy = NONE, the old reasoning said "already hidden by the strategy";
        // yet a component is always drawn, and @UMLExclude is the only way of silencing it.
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.NONE)
            record Point(@UMLExclude int x, int y) {}
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void decoratingAComponent_neverMakesItInvisible(@TempDir Path tmp) throws IOException {
        // The question "will this member be drawn?" is now asked once only, for
        // the four decorations. A record component is drawn whatever the
        // strategy: declaring it invisible would be a contradiction, and under strategy = NONE it is
        // the only case where the reasoning could tip over at once for all four.
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
        // The annotation written on the component is cloned by javac onto the field and onto
        // the accessor: a warning would point at a line the author did not write.
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.NONE)
            record Point(@UMLInclude int x, int y) {}
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }
}
