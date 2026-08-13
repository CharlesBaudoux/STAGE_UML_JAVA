package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// Les deux composantes que le compte rendu a gagnées, et l'absence qu'il ne dit plus par un null.
//
// RapportDeGenerationTest, à côté, porte sur ce que summary() affiche. Ce fichier-ci porte sur la
// forme du record : ce qu'il refuse, et ce qu'une absence légitime a le droit de valoir.
class RapportDeGenerationInvariantsTest {

    private static final ImmutableList<Path> UN_FICHIER =
        ImmutableList.of(Path.of("diagrammes", "projet.svg"));

    @Test
    void unFichierDesIgnoreesNull_estRefuse() {
        // C'était le seul null de toute la zone exportée : rien, dans la signature, ne prévenait
        // l'appelant qu'il devait s'y attendre. L'absence se dit maintenant par un Optional vide.
        assertThrows(NullPointerException.class, () -> new RapportDeGeneration(1,
            ImmutableList.of(), UN_FICHIER, null, ImmutableList.of()));
    }

    @Test
    void desAvertissementsNull_sontRefuses() {
        assertThrows(NullPointerException.class, () -> new RapportDeGeneration(1,
            ImmutableList.of(), UN_FICHIER, Optional.empty(), null));
    }

    @Test
    void laFormeCourte_neDitNiFichierDesIgnoreesNiAvertissement() {
        // Le constructeur commode, celui d'un compte rendu qu'on fabrique à la main : les deux
        // composantes ajoutées y valent « rien à dire », et non « on ne sait pas ».
        RapportDeGeneration rapport =
            new RapportDeGeneration(3, ImmutableList.of(), UN_FICHIER);

        assertEquals(Optional.empty(), rapport.fichierDesIgnorees());
        assertEquals(ImmutableList.of(), rapport.avertissements());
    }

    @Test
    void sansFichierDesIgnorees_leResumeNEnParlePas() {
        String resume = new RapportDeGeneration(3, ImmutableList.of(), UN_FICHIER).summary();

        assertFalse(resume.contains("full list"), resume);
    }

    @Test
    void avecUnFichierDesIgnorees_leResumeLeNomme() {
        // La ligne qui dit où trouver la liste appartient au compte rendu, et non à la sortie
        // standard : un appelant qui le relaie ailleurs — le plugin Maven dans son log — la
        // perdait en chemin.
        Path liste = Path.of("diagrammes", UMLGenerator.FICHIER_DES_CLASSES_IGNOREES);

        String resume = new RapportDeGeneration(3, ImmutableList.of("Oubliee"), UN_FICHIER,
            Optional.of(liste), ImmutableList.of()).summary();

        assertTrue(resume.contains(liste.toString()), resume);
    }

    @Test
    void lesAvertissements_sontRendusTelsQuels() {
        RapportDeGeneration rapport = new RapportDeGeneration(3, ImmutableList.of(), UN_FICHIER,
            Optional.empty(), ImmutableList.of("la vue est illisible", "règle sans effet"));

        assertEquals(ImmutableList.of("la vue est illisible", "règle sans effet"),
            rapport.avertissements());
    }
}
