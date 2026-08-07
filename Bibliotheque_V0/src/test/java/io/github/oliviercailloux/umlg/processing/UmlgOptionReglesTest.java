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

// L'option -Aumlg.rules : ce que le processeur retrouve quand on lui donne le fichier de règles.
//
// CE QUE CES TESTS PROTÈGENT
//
// Le défaut de @UMLClass est PROJECT, c'est-à-dire « la stratégie que déclare umlg.rules ». Ce
// fichier vit à la racine des classes compilées, et javac ne sait pas où il se trouve : sans
// l'option, le processeur ne peut pas dire si un membre sera dessiné, et se tait sur tout ce qui
// en dépend. L'option lui rend la vue, et c'est la seule chose que la bibliothèque taise.
//
// Chaque test compare donc les deux mondes : sans l'option, silence ; avec, le même avertissement
// que si la classe avait déclaré sa stratégie en toutes lettres.
class UmlgOptionReglesTest {

    private static final String ENTETE = "import io.github.oliviercailloux.umlg.annotation.*;\n";

    // Une classe qui s'en remet au projet, et un @UMLInclude sur un membre public. Utile ou non
    // selon ce que le fichier déclare : c'est tout le cas d'espèce.
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
        // Le comportement par défaut : indécidable, donc muet. Rien n'est faux pour autant.
        Verdict verdict = compiler(tmp, List.of());

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void avecLOption_lAvertissementRevient(@TempDir Path tmp) throws IOException {
        // « strategy public » affiche déjà l'attribut : le @UMLInclude ne déroge à rien, et le
        // processeur peut enfin le dire.
        Path regles = ecrireRegles(tmp, "strategy public\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("@UMLInclude has no effect"), verdict.rapport());
        assertTrue(verdict.avertissementContient("PUBLIC"),
            "Le message doit nommer la stratégie réellement appliquée : " + verdict.rapport());
    }

    @Test
    void avecUnProjetQuiNAfficheRien_rienNestSignale(@TempDir Path tmp) throws IOException {
        // L'autre moitié, celle qui compte autant : le @UMLInclude est ici indispensable, et le
        // processeur doit se taire. Un avertissement serait un contresens.
        Path regles = ecrireRegles(tmp, "strategy none\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void fichierIntrouvable_estSignaleSansArreterLaCompilation(@TempDir Path tmp)
        throws IOException {
        // L'utilisateur a nommé un fichier qu'il n'a pas écrit : son code reste juste, mais il
        // attend des vérifications qu'il n'aura pas. Un avertissement, donc, et non une erreur.
        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + tmp.resolve("absent.rules")));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("no such file"), verdict.rapport());
    }

    @Test
    void fichierFautif_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        // Le même fichier arrêterait la génération pour la même raison : le dire ici le dit plus
        // tôt, ce qui est tout l'intérêt du processeur.
        Path regles = ecrireRegles(tmp, "couleur Client -> rouge\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertFalse(verdict.reussie(), "Un fichier de règles fautif ne décrit aucun diagramme.");
        assertTrue(verdict.erreurContient("unknown rule"), verdict.rapport());
    }

    @Test
    void avertissementsEteints_taisentAussiCeuxQueLOptionRend(@TempDir Path tmp)
        throws IOException {
        // -Aumlg.warnings=none garde son sens : il éteint tout, y compris ce que l'option vient
        // de rendre audible. Les erreurs, elles, ne s'éteignent jamais.
        Path regles = ecrireRegles(tmp, "strategy public\n");

        Verdict verdict =
            compiler(tmp, List.of("-Aumlg.rules=" + regles, "-Aumlg.warnings=none"));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }
}
