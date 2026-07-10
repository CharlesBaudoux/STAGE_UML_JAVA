package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlantUmlWriterTest {

    // On crée un vrai cobaye valide pour tromper la sécurité de UmlEntity
    @UMLClass
    static class ClasseCobaye {}

    @Test
    void testGenerationFichierSvg(@TempDir Path dossierTemporaire) throws Exception {
        UmlEntity fausseEntite = EntityExtractor.extract(ClasseCobaye.class);
        UmlProject projet = UmlProject.given(Set.of(fausseEntite));
        
        Path cheminFichierSortie = dossierTemporaire.resolve("diagramme_test.svg");
        PlantUmlWriter.write(projet, cheminFichierSortie.toString());

        assertTrue(Files.exists(cheminFichierSortie));
        assertTrue(Files.size(cheminFichierSortie) > 0);
    }

    @Test
    void testGenerationFichierPng(@TempDir Path dossierTemporaire) throws Exception {
        UmlEntity fausseEntite = EntityExtractor.extract(ClasseCobaye.class);
        UmlProject projet = UmlProject.given(Set.of(fausseEntite));
        
        Path cheminFichierSortie = dossierTemporaire.resolve("diagramme_test.png");
        PlantUmlWriter.write(projet, cheminFichierSortie.toString());

        assertTrue(Files.exists(cheminFichierSortie));
        assertTrue(Files.size(cheminFichierSortie) > 0);
    }

    @Test
    void testEchecRapideSiArgumentsNuls() {
        assertThrows(NullPointerException.class, () -> {
            PlantUmlWriter.write(null, "output.svg");
        });

        assertThrows(NullPointerException.class, () -> {
            UmlEntity fausseEntite = EntityExtractor.extract(ClasseCobaye.class);
            UmlProject projetValide = UmlProject.given(Set.of(fausseEntite));
            PlantUmlWriter.write(projetValide, null);
        });
    }
}