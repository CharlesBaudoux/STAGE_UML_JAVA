package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScannerTest {

    @UMLClass
    static class ClasseCobayeAnnotee {}

    static class ClasseCobayeIgnoree {}

    @Test
    void testScanDossierValide() throws Exception {
        Path cheminTestClasses = Paths.get("target", "test-classes");

        UmlProject projet = ProjectScanner.scan(cheminTestClasses).projet();

        boolean cobayeAnnoteTrouve = projet.entities().stream()
                .anyMatch(e -> e.name().contains("ClasseCobayeAnnotee"));

        boolean cobayeIgnoreTrouve = projet.entities().stream()
                .anyMatch(e -> e.name().contains("ClasseCobayeIgnoree"));

        assertTrue(cobayeAnnoteTrouve,
            "Le scanner aurait dû détecter la classe annotée @UMLClass.");
        assertFalse(cobayeIgnoreTrouve,
            "Le scanner n'aurait pas dû embarquer la classe sans annotation.");
    }

    @Test
    void leScan_rendCeQuIlALaisseDeCote() throws Exception {
        ResultatDeScan resultat = ProjectScanner.scan(Paths.get("target", "test-classes"));

        assertTrue(resultat.classesIgnorees().contains("ClasseCobayeIgnoree"),
            resultat.classesIgnorees().toString());
        assertFalse(resultat.classesIgnorees().contains("ClasseCobayeAnnotee"),
            resultat.classesIgnorees().toString());
    }

    @Test
    void leResultat_refuseUneComposanteNulle() {
        UmlProject projet =
            UmlProject.given(Set.of(EntityExtractor.extract(ClasseCobayeAnnotee.class)));

        assertThrows(NullPointerException.class,
            () -> new ResultatDeScan(null, ImmutableList.of()));
        assertThrows(NullPointerException.class, () -> new ResultatDeScan(projet, null));
    }

    @Test
    void testScanRejetteDossierInexistant() {
        Path dossierFantome = Paths.get("target", "un-dossier-qui-n-existe-pas");

        assertThrows(IllegalArgumentException.class, () -> {
            ProjectScanner.scan(dossierFantome).projet();
        }, "Le scanner doit lever une IllegalArgumentException si le dossier n'existe pas.");
    }

    @Test
    void testScanRejetteFichierSimple(@TempDir Path dossierTemporaire) throws Exception {
        Path fichierSimple = dossierTemporaire.resolve("texte.txt");
        Files.writeString(fichierSimple, "Contenu d'un fichier classique");

        assertThrows(IllegalArgumentException.class, () -> {
            ProjectScanner.scan(fichierSimple).projet();
        }, "Le scanner doit refuser un chemin qui pointe vers un fichier au lieu d'un répertoire.");
    }

    @Test
    void testScanRejetteNull() {
        assertThrows(NullPointerException.class, () -> {
            ProjectScanner.scan(null).projet();
        }, "Le scanner doit lever une NullPointerException si le chemin fourni est null.");
    }
}
