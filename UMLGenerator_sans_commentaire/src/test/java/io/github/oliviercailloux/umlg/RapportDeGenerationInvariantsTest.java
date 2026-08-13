package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RapportDeGenerationInvariantsTest {

    private static final ImmutableList<Path> UN_FICHIER =
        ImmutableList.of(Path.of("diagrammes", "projet.svg"));

    @Test
    void unFichierDesIgnoreesNull_estRefuse() {
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
        RapportDeGeneration rapport =
            new RapportDeGeneration(3, ImmutableList.of(), UN_FICHIER);

        assertEquals(Optional.empty(), rapport.fichierDesIgnorees());
        assertEquals(ImmutableList.of(), rapport.avertissements());
    }

    @Test
    void sansFichierDesIgnorees_leResumeNEnParlePas() {
        String resume = new RapportDeGeneration(3, ImmutableList.of(), UN_FICHIER).resume();

        assertFalse(resume.contains("full list"), resume);
    }

    @Test
    void avecUnFichierDesIgnorees_leResumeLeNomme() {
        Path liste = Path.of("diagrammes", UMLGenerator.FICHIER_DES_CLASSES_IGNOREES);

        String resume = new RapportDeGeneration(3, ImmutableList.of("Oubliee"), UN_FICHIER,
            Optional.of(liste), ImmutableList.of()).resume();

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
