package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

// Le compte rendu de fin de génération.
//
// Il a remplacé une ligne « Success » par image, qui n'était juste que du temps où l'on n'en
// écrivait qu'une : douze vues donnaient douze lignes presque identiques, et la seule information
// utile s'y perdait.
class RapportDeGenerationTest {

    private static RapportDeGeneration rapport(int lues, int ignorees, int fichiers) {
        return new RapportDeGeneration(lues,
            IntStream.range(0, ignorees).mapToObj(i -> "Ignoree" + i)
                .collect(ImmutableList.toImmutableList()),
            IntStream.range(0, fichiers).mapToObj(i -> Path.of("diagrammes", "v" + i + ".svg"))
                .collect(ImmutableList.toImmutableList()));
    }

    @Test
    void leCompteRendu_ditCombienDeFichiersEtOu() {
        String resume = rapport(21, 0, 12).resume();

        assertTrue(resume.contains("12 diagram(s) written"), resume);
        assertTrue(resume.contains("diagrammes"), "Le dossier doit être nommé : " + resume);
    }

    @Test
    void unFichierSansDossier_estNommeLuiMeme() {
        // Le cas du fichier unique par défaut, « diagramme_architecture.svg » : un chemin d'un
        // seul segment n'a pas de parent, et le compte rendu écrivait « written to null » — le
        // tout premier message que voyait un utilisateur n'ayant rien configuré.
        String resume = new RapportDeGeneration(5, ImmutableList.of(),
            ImmutableList.of(Path.of("diagramme_architecture.svg"))).resume();

        assertFalse(resume.contains("null"), resume);
        assertTrue(resume.contains("diagramme_architecture.svg"), resume);
    }

    @Test
    void leCompteRendu_ditCeQuiAEteIgnore() {
        // L'information que personne n'avait : une classe sans @UMLClass est hors du diagramme,
        // et rien ne distinguait celle qu'on a écartée de celle qu'on a oubliée.
        String resume = rapport(21, 23, 12).resume();

        assertTrue(resume.contains("21 class(es) drawn"), resume);
        assertTrue(resume.contains("23 ignored"), resume);
    }

    @Test
    void peuDeClassesIgnorees_sontNommees() {
        String resume = rapport(5, 2, 1).resume();

        assertTrue(resume.contains("Ignoree0"), resume);
        assertTrue(resume.contains("Ignoree1"), resume);
    }

    @Test
    void beaucoupDeClassesIgnorees_sontComptéesSansEtreNommees() {
        // Au-delà d'une douzaine, la liste noierait le reste ; le nombre suffit à savoir qu'il
        // faut aller voir.
        String resume = rapport(5, 40, 1).resume();

        assertTrue(resume.contains("40 ignored"), resume);
        assertFalse(resume.contains("Ignoree0"), resume);
    }

    @Test
    void aucuneClasseIgnoree_neLaisseAucuneListe() {
        String resume = rapport(5, 0, 1).resume();

        assertTrue(resume.contains("0 ignored"), resume);
        assertFalse(resume.contains(" : "), resume);
    }
}
