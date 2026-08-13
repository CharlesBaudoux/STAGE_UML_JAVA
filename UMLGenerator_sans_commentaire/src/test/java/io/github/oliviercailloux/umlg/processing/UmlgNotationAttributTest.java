package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgNotationAttributTest {

    private static Verdict compiler(Path tmp, String code) throws IOException {
        return CompilationCobaye.compilerCorps(tmp, code);
    }

    @Test
    void unAccesseurEnNotationAttribut_compileSansUnMot(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Clavier {
                @UMLInclude(asAttribute = true)
                public Map<String, Short> codeByName() { return Map.of(); }
            }
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void uneMethodeAParametres_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Clavier {
                @UMLInclude(asAttribute = true)
                public String canonical(String keyName) { return keyName; }
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("takes parameters"), verdict.rapport());
    }

    @Test
    void uneMethodeSansRetour_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Clavier {
                @UMLInclude(asAttribute = true)
                public void valider() {}
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("returns nothing"), verdict.rapport());
    }

    @Test
    void surUnAttribut_laNotationEstRefusee(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Clavier {
                @UMLInclude(asAttribute = true)
                public String nom;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("already an attribute"), verdict.rapport());
    }


    @Test
    void exclureUnComposant_nEstPasSansEffet(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.NONE)
            record Point(@UMLExclude int x, int y) {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void decorerUnComposant_neLeDitJamaisInvisible(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.NONE)
            @UMLConstraint(rule = "x >= 0")
            record Point(@UMLDerived @UMLMultiplicity("1") @UMLNote(text = "abscisse") int x) {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void inclureUnComposant_resteSilencieux(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.NONE)
            record Point(@UMLInclude int x, int y) {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }
}
