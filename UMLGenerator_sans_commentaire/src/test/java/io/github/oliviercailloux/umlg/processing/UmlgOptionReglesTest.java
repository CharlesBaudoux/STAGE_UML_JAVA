package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgOptionReglesTest {

    private static final String ENTETE = "import io.github.oliviercailloux.umlg.annotation.*;\n";

    private static final String COBAYE = """
        @UMLClass
        class Client {
            @UMLInclude
            public String nom;
        }
        """;

    private static Path ecrireRegles(Path tmp, String contenu) throws IOException {
        Path fichier = tmp.resolve("umlg.rules");
        Files.writeString(fichier, contenu, StandardCharsets.UTF_8);
        return fichier;
    }

    private static Verdict compiler(Path tmp, List<String> options) throws IOException {
        return CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + COBAYE, options);
    }

    @Test
    void sansLOption_leProcesseurSeTait(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, List.of());

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void avecLOption_lAvertissementRevient(@TempDir Path tmp) throws IOException {
        Path regles = ecrireRegles(tmp, "strategy public\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("@UMLInclude has no effect"), verdict.rapport());
        assertTrue(verdict.avertissementContient("PUBLIC"),
            "Le message doit nommer la stratégie réellement appliquée : " + verdict.rapport());
    }

    @Test
    void avecUnProjetQuiNAfficheRien_rienNestSignale(@TempDir Path tmp) throws IOException {
        Path regles = ecrireRegles(tmp, "strategy none\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void fichierIntrouvable_estSignaleSansArreterLaCompilation(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + tmp.resolve("absent.rules")));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("no such file"), verdict.rapport());
    }

    @Test
    void fichierFautif_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Path regles = ecrireRegles(tmp, "couleur Client -> rouge\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertFalse(verdict.reussie(), "Un fichier de règles fautif ne décrit aucun diagramme.");
        assertTrue(verdict.erreurContient("unknown rule"), verdict.rapport());
    }

    @Test
    void avertissementsEteints_taisentAussiCeuxQueLOptionRend(@TempDir Path tmp)
        throws IOException {
        Path regles = ecrireRegles(tmp, "strategy public\n");

        Verdict verdict =
            compiler(tmp, List.of("-Aumlg.rules=" + regles, "-Aumlg.warnings=none"));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }
}
