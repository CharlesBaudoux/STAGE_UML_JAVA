package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgEnTeteDeClasseTest {

    private static final String ENTETE = "import io.github.oliviercailloux.umlg.annotation.*;\n";

    private static Verdict compiler(Path tmp, String code) throws IOException {
        return CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + code);
    }

    @Test
    void nomFaitDeCaracteresInvisibles_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(name = "\\1\\2")
            class Client {}
            """);

        assertFalse(verdict.reussie(), "Une boîte au nom invisible ne se dessine pas.");
        assertTrue(verdict.erreurContient("name() must show something"), verdict.rapport());
    }

    @Test
    void stereotypeFaitDeCaracteresInvisibles_arreteLaCompilation(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(stereotype = "\\1\\2")
            class Client {}
            """);

        assertFalse(verdict.reussie(), "« <<>> » est refusé par le moteur de dessin.");
        assertTrue(verdict.erreurContient("stereotype() must show something"), verdict.rapport());
    }

    @Test
    void unNomOrdinaireAvecUnCaractereDeControle_compile(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(name = "Compte\\1", stereotype = "entity\\1")
            class Client {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
    }

    @Test
    void unNomNonEcrit_neSeVoitRienReprocher(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
    }
}
