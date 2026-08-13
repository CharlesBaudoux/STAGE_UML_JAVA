package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.Avertissements;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScannerTest {

    @UMLClass
    static class ClasseCobayeAnnotee {}

    static class ClasseCobayeIgnoree {}

    private static final Path TEST_CLASSES = Paths.get("target", "test-classes");

    private static ImmutableList<String> noms(UmlProject projet) {
        return projet.entities().stream().map(UmlEntity::name)
            .collect(ImmutableList.toImmutableList());
    }

    @Test
    void leScan_retientLesClassesAnnoteesEtLaisseLesAutres() throws Exception {
        UmlProject projet = ProjectScanner.scan(TEST_CLASSES).projet();

        ImmutableList<String> noms = noms(projet);

        assertTrue(noms.contains("ClasseCobayeAnnotee"),
            "La classe portant @UMLClass doit avoir sa boîte : " + noms);
        assertFalse(noms.contains("ClasseCobayeIgnoree"),
            "La classe sans annotation ne doit pas en avoir : " + noms);
    }

    @Test
    void leScan_rendCeQuIlALaisseDeCote() throws Exception {
        ResultatDeScan resultat = ProjectScanner.scan(TEST_CLASSES);

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
    void unDossierInexistant_estRefuseEnLeNommant() {
        Path dossierFantome = Paths.get("target", "un-dossier-qui-n-existe-pas");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ProjectScanner.scan(dossierFantome));

        assertTrue(e.getMessage().contains("un-dossier-qui-n-existe-pas"), e.getMessage());
    }

    @Test
    void unFichierAuLieuDunDossier_estRefuseEnLeNommant(@TempDir Path dossierTemporaire)
        throws Exception {
        Path fichierSimple = dossierTemporaire.resolve("texte.txt");
        Files.writeString(fichierSimple, "Contenu d'un fichier classique");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ProjectScanner.scan(fichierSimple));

        assertTrue(e.getMessage().contains("texte.txt"), e.getMessage());
    }

    @Test
    void unCheminNul_estRefuse() {
        assertThrows(NullPointerException.class, () -> ProjectScanner.scan(null));
    }

    @Test
    void unClasspathNul_estRefuse() {
        assertThrows(NullPointerException.class, () -> ProjectScanner.scan(TEST_CLASSES, null));
    }


    private static String ditPar(Runnable signalement) {
        List<String> dits = new ArrayList<>();
        try (Avertissements.Canal canal = Avertissements.versLeCanal(dits::add)) {
            signalement.run();
        }
        assertEquals(1, dits.size(), dits.toString());
        return dits.get(0);
    }

    @Test
    void aucuneBoitePerdue_neDitRien() {
        List<String> dits = new ArrayList<>();
        try (Avertissements.Canal canal = Avertissements.versLeCanal(dits::add)) {
            assertFalse(ProjectScanner.signalerBoitesPerdues(ImmutableList.of()));
        }

        assertEquals(List.of(), dits);
    }

    @Test
    void uneBoitePerdue_estNommeeAvecSaCause() {
        String dit = ditPar(() -> ProjectScanner.signalerBoitesPerdues(ImmutableList.of(
            "com.exemple.Porteuse (java.lang.TypeNotPresentException: Type com.ex.B not present)")));

        assertTrue(dit.startsWith("1 class(es) loaded but could not be read"), dit);
        assertTrue(dit.contains("com.exemple.Porteuse"), dit);
        assertTrue(dit.contains("Type com.ex.B not present"), dit);
        assertFalse(dit.contains("only readable on a loaded class"), dit);
    }

    @Test
    void tropDeBoitesPerdues_sontCompteesEtNonNommees() {
        ImmutableList<String> trop =
            IntStream.rangeClosed(0, ProjectScanner.SEUIL_ENUMERATION)
                .mapToObj(i -> "com.exemple.C" + i)
                .collect(ImmutableList.toImmutableList());

        String dit = ditPar(() -> ProjectScanner.signalerBoitesPerdues(trop));

        assertTrue(dit.startsWith(trop.size() + " class(es) loaded but could not be read"), dit);
        assertFalse(dit.contains("com.exemple.C0"), dit);
    }
}
