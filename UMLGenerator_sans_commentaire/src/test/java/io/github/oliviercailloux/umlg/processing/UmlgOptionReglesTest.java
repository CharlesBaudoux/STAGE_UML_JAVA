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

    private static final String COBAYE = """
        @UMLClass
        class Client {
            @UMLInclude
            public String nom;
        }
        """;

    private static Path ecrireRegles(Path tmp, String contenu) throws IOException {
        Path fichier = tmp.resolve("umlg.json");
        Files.writeString(fichier, contenu, StandardCharsets.UTF_8);
        return fichier;
    }

    private static Verdict compiler(Path tmp, List<String> options) throws IOException {
        return CompilationCobaye.compilerCorps(tmp, COBAYE, options);
    }

    @Test
    void sansLOption_leProcesseurSeTait(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, List.of());

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void avecLOption_lAvertissementRevient(@TempDir Path tmp) throws IOException {
        Path regles = ecrireRegles(tmp, "{ \"strategy\": \"public\" }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("@UMLInclude has no effect"), verdict.rapport());
        assertTrue(verdict.avertissementContient("PUBLIC"),
            "Le message doit nommer la stratégie réellement appliquée : " + verdict.rapport());
    }

    @Test
    void avecUnProjetQuiNAfficheRien_rienNestSignale(@TempDir Path tmp) throws IOException {
        Path regles = ecrireRegles(tmp, "{ \"strategy\": \"none\" }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void unMotifQuiMasqueLeChamp_neReprochePasLInclude(@TempDir Path tmp) throws IOException {
        Path regles = ecrireRegles(tmp, "{ \"strategy\": \"public\", \"hide\": [\"nom\"] }");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void surUneAssociationDansLaBoite_lAvertissementDependAussiDesRegles(@TempDir Path tmp)
        throws IOException {
        String corps = """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLInclude
                @UMLAssociation(target = Commande.class, notation = UMLNotation.ATTRIBUTE)
                private Commande commande;
            }
            """;
        Path regles = ecrireRegles(tmp, "{ \"strategy\": \"public\" }");

        Verdict avecRegles = CompilationCobaye.compilerCorps(tmp, corps,
            List.of("-Aumlg.rules=" + regles));

        assertTrue(avecRegles.reussie(), avecRegles.rapport());
        assertTrue(avecRegles.avertissementContient("already shown in the attribute compartment"),
            avecRegles.rapport());

        Path masquant = ecrireRegles(tmp, "{ \"strategy\": \"public\", \"hide\": [\"commande\"] }");

        Verdict masque = CompilationCobaye.compilerCorps(tmp, corps,
            List.of("-Aumlg.rules=" + masquant));

        assertTrue(masque.reussie(), masque.rapport());
        assertTrue(masque.avertissements().isEmpty(), masque.rapport());
    }

    @Test
    void lExcludeSansEffet_parleSansLesRegles(@TempDir Path tmp) throws IOException {
        Verdict verdict = CompilationCobaye.compilerCorps(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLExclude
                private String motDePasse;
            }
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("@UMLExclude has no effect"), verdict.rapport());
    }

    @Test
    void fichierIntrouvable_estSignaleSansArreterLaCompilation(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + tmp.resolve("absent.json")));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("no such file"), verdict.rapport());
    }

    @Test
    void cheminRelatifIntrouvable_ditOuIlAChercher(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=regles/umlg.json"));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("looked for "), verdict.rapport());
        assertTrue(verdict.avertissementContient(
            Path.of("regles/umlg.json").toAbsolutePath().toString()), verdict.rapport());
        assertTrue(verdict.avertissementContient("working directory"), verdict.rapport());
    }

    @Test
    void nowarn_neFaitPasTaireLAvertissementDeConfiguration(@TempDir Path tmp) throws IOException {
        Verdict verdict = CompilationCobaye.compilerCorps(tmp, """
            @UMLClass(stereotype = "<<Service>>")
            class Paiement {}
            """, List.of("-nowarn", "-Aumlg.rules=" + tmp.resolve("absent.json")));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("no such file"), verdict.rapport());
        assertTrue(verdict.avertissements().stream()
            .noneMatch(message -> message.contains("already contains the guillemets")),
            "-nowarn doit taire ce que le processeur a trouvé dans le code : " + verdict.rapport());
    }

    @Test
    void fichierFautif_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Path regles = ecrireRegles(tmp, "{ \"couleur\": { \"Client\": \"rouge\" } }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertFalse(verdict.reussie(), "Un fichier de règles fautif ne décrit aucun diagramme.");
        assertTrue(verdict.erreurContient("unknown rule"), verdict.rapport());
    }

    @Test
    void avertissementsEteints_taisentAussiCeuxQueLOptionRend(@TempDir Path tmp)
        throws IOException {
        Path regles = ecrireRegles(tmp, "{ \"strategy\": \"public\" }\n");

        Verdict verdict =
            compiler(tmp, List.of("-Aumlg.rules=" + regles, "-Aumlg.warnings=none"));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }
}
