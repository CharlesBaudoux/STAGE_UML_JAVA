package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// L'en-tête d'une boîte — name, stereotype — vu du processeur, et la question de savoir s'il la
// juge comme la génération.
//
// Le processeur posait la question avec isBlank(), qui ne tient pas les caractères de contrôle
// pour des blancs ; la génération, elle, la pose au texte échappé, où ces caractères ont disparu.
// Le même code recevait donc deux réponses : « name = "\1" » compilait, puis échouait au dessin
// sur « class "" as C », que le moteur refuse. La règle vit désormais dans
// ClassHeaderValidator, et les deux moitiés la lisent au même endroit.
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
        // La règle ne se durcit pas : il suffit qu'il reste quelque chose à dessiner.
        Verdict verdict = compiler(tmp, """
            @UMLClass(name = "Compte\\1", stereotype = "entity\\1")
            class Client {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
    }

    @Test
    void unNomNonEcrit_neSeVoitRienReprocher(@TempDir Path tmp) throws IOException {
        // La chaîne vide est le défaut de name() : elle veut dire « le nom de la classe », et non
        // « une boîte sans nom ». C'est la seule différence entre les deux moitiés, et elle est
        // dans l'appelant, non dans la règle.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
    }
}
