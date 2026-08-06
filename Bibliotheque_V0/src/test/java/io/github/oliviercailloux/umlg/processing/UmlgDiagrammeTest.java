package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Ce que le processeur dit d'une @UMLDiagram, à la compilation du package-info qui la porte.
//
// L'annotation étant la seule à viser un paquetage, ses cobayes sont des package-info.java et
// non des classes : la déclaration de paquetage y précède les imports, et l'annotation précède
// les deux.
class UmlgDiagrammeTest {

    // Un package-info.java : l'annotation précède la déclaration de paquetage, laquelle précède
    // les imports. C'est la seule forme que Java accepte pour annoter un paquetage.
    private static String packageInfo(String paquetage, String parametres) {
        return "@UMLDiagram(" + parametres + ")\n"
            + "package " + paquetage + ";\n\n"
            + "import io.github.oliviercailloux.umlg.annotation.UMLDiagram;\n"
            + "import io.github.oliviercailloux.umlg.annotation.UMLFlow;\n";
    }

    private static Verdict compiler(Path tmp, String parametres) throws IOException {
        return CompilationCobaye.compiler(tmp, "package-info.java",
            packageInfo("com.exemple", parametres));
    }

    @Test
    void diagrammeCorrect_compileSansRienSignaler(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp,
            "title = \"Architecture du module Facturation\", direction = UMLFlow.LEFT_TO_RIGHT");

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void titreBlanc_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        // Un titre d'espaces réserverait en tête du dessin une place que rien n'occuperait.
        Verdict verdict = compiler(tmp, "title = \"   \"");

        assertFalse(verdict.reussie(), "Un titre fait d'espaces ne décrit aucun diagramme.");
        assertTrue(verdict.erreurContient("whitespace"), verdict.rapport());
    }

    @Test
    void titreVide_estAccepte(@TempDir Path tmp) throws IOException {
        // La chaîne vide est le défaut du paramètre : elle dit « pas de titre », ce qui est une
        // demande parfaitement claire. Seul l'autre paramètre écrit rend l'annotation utile.
        Verdict verdict = compiler(tmp, "title = \"\", hideEmptyMembers = false");

        assertTrue(verdict.reussie(), verdict.rapport());
    }

    @Test
    void annotationSansAucunParametre_estSignalee(@TempDir Path tmp) throws IOException {
        // Elle décrit exactement le diagramme qu'on obtiendrait sans elle : ce n'est pas une
        // faute, mais personne n'écrit une annotation pour n'obtenir aucun effet.
        Verdict verdict = compiler(tmp, "");

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("no parameter is set"), verdict.rapport());
    }

    @Test
    void avertissementsEteints_taisentCeluiCi(@TempDir Path tmp) throws IOException {
        // -Aumlg.warnings=none doit valoir pour cet avertissement comme pour les autres : il
        // passe par le même Rapporteur, et ce test le prouve plutôt que de le supposer.
        Verdict verdict = CompilationCobaye.compiler(tmp, "package-info.java",
            packageInfo("com.exemple", ""), List.of("-Aumlg.warnings=none"));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void deuxPaquetagesAnnotes_arretentLaCompilation(@TempDir Path tmp) throws IOException {
        // Les deux sont compilés dans la même tâche, donc devant le même processeur : c'est la
        // seule façon pour lui de voir la contradiction. Une compilation incrémentale peut ne
        // lui en montrer qu'un, et c'est pourquoi le scan refait le contrôle de son côté.
        //
        // Les annotations de paquetage n'ont droit qu'à un fichier nommé package-info.java, d'où
        // deux répertoires distincts.
        Verdict verdict = CompilationCobaye.compilerTous(tmp, Map.of(
            "com/exemple/package-info.java", packageInfo("com.exemple", "title = \"Premier\""),
            "com/autre/package-info.java", packageInfo("com.autre", "title = \"Second\"")));

        assertFalse(verdict.reussie(), "Un projet ne décrit qu'un diagramme.");
        assertTrue(verdict.erreurContient("already carries @UMLDiagram"), verdict.rapport());
    }
}
