package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

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
        String resume = new RapportDeGeneration(5, ImmutableList.of(),
            ImmutableList.of(Path.of("diagramme_architecture.svg"))).resume();

        assertFalse(resume.contains("null"), resume);
        assertTrue(resume.contains("diagramme_architecture.svg"), resume);
    }

    @Test
    void unSeulFichierDansUnDossier_estNommeLuiAussi() {
        String resume = new RapportDeGeneration(5, ImmutableList.of(),
            ImmutableList.of(Path.of("docs", "archi.svg"))).resume();

        assertTrue(resume.contains(Path.of("docs", "archi.svg").toString()), resume);
    }

    @Test
    void unSeulFichierPrefixeDePointSlash_estNommeLuiAussi() {
        String resume = new RapportDeGeneration(5, ImmutableList.of(),
            ImmutableList.of(Path.of("./diagramme.svg"))).resume();

        assertTrue(resume.contains("diagramme.svg"), resume);
    }

    @Test
    void plusieursFichiers_sontResumesParLeurDossier() {
        String resume = rapport(21, 0, 12).resume();

        assertTrue(resume.contains("diagrammes"), resume);
        assertFalse(resume.contains("v0.svg"), "Les vues ne doivent pas être listées : " + resume);
    }

    @Test
    void leCompteRendu_ditCeQuiAEteIgnore() {
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
