package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// La notation attribut — @UMLInclude(asAttribute = true) — vue du processeur, et le sort des
// composants de record dans les avertissements « sans effet ».
//
// La notation exige une méthode sans paramètre qui rend quelque chose : les parenthèses
// retirées, il ne reste que le nom et le type, et il faut que les deux existent. Les trois
// écarts sont des erreurs — la génération les refuserait pareil, le processeur le dit à la
// compilation, c'est tout son intérêt.
class UmlgNotationAttributTest {

    private static final String ENTETE = "import io.github.oliviercailloux.umlg.annotation.*;\n"
        + "import java.util.*;\n";

    private static Verdict compiler(Path tmp, String code) throws IOException {
        return CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + code);
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

    // --- Les composants de record, dans les avertissements « sans effet » ---

    @Test
    void exclureUnComposant_nEstPasSansEffet(@TempDir Path tmp) throws IOException {
        // Sous strategy = NONE, l'ancien raisonnement disait « déjà caché par la stratégie » ;
        // or un composant est toujours dessiné, et @UMLExclude est le seul moyen de le taire.
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.NONE)
            record Point(@UMLExclude int x, int y) {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void decorerUnComposant_neLeDitJamaisInvisible(@TempDir Path tmp) throws IOException {
        // La question « ce membre sera-t-il dessiné ? » est désormais posée une seule fois, pour
        // les quatre décorations. Un composant de record est dessiné quelle que soit la
        // stratégie : le déclarer invisible serait un contresens, et sous strategy = NONE c'est
        // le seul cas où le raisonnement pouvait basculer d'un coup pour les quatre.
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
        // L'annotation écrite sur le composant est clonée par javac sur le champ et sur
        // l'accesseur : un avertissement désignerait une ligne que l'auteur n'a pas écrite.
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.NONE)
            record Point(@UMLInclude int x, int y) {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }
}
