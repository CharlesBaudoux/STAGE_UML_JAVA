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

// Le tri opéré par le scanner, et ses refus d'entrée.
//
// Astuce de ce test : il scanne target/test-classes, c'est-à-dire ses propres classes compilées.
// Les deux cobayes déclarés ci-dessous s'y trouvent donc réellement sous forme de .class, ce qui
// évite d'avoir à fabriquer un répertoire d'exemple.
class ProjectScannerTest {

    // Une classe interne annotée : elle DOIT être détectée.
    @UMLClass
    static class ClasseCobayeAnnotee {}

    // Une classe interne NON annotée : elle DOIT être ignorée.
    static class ClasseCobayeIgnoree {}

    private static final Path TEST_CLASSES = Paths.get("target", "test-classes");

    // Les noms affichés des boîtes rendues par un scan : la sortie sur laquelle porter les
    // assertions.
    private static ImmutableList<String> noms(UmlProject projet) {
        return projet.entities().stream().map(UmlEntity::name)
            .collect(ImmutableList.toImmutableList());
    }

    @Test
    void leScan_retientLesClassesAnnoteesEtLaisseLesAutres() throws Exception {
        UmlProject projet = ProjectScanner.scan(TEST_CLASSES).projet();

        // Le répertoire contient les cobayes de toute la suite : on ne peut pas comparer
        // l'ensemble des boîtes, mais on peut dire ce que celles-ci contiennent et ne contiennent
        // pas — les deux moitiés du tri.
        ImmutableList<String> noms = noms(projet);

        assertTrue(noms.contains("ClasseCobayeAnnotee"),
            "La classe portant @UMLClass doit avoir sa boîte : " + noms);
        assertFalse(noms.contains("ClasseCobayeIgnoree"),
            "La classe sans annotation ne doit pas en avoir : " + noms);
    }

    @Test
    void leScan_rendCeQuIlALaisseDeCote() throws Exception {
        // Ce que portait la variable de fil, désormais dans le résultat : la classe non annotée
        // n'est pas dans le modèle, et c'est justement ce qu'il y a à en dire.
        ResultatDeScan resultat = ProjectScanner.scan(TEST_CLASSES);

        assertTrue(resultat.classesIgnorees().contains("ClasseCobayeIgnoree"),
            resultat.classesIgnorees().toString());
        assertFalse(resultat.classesIgnorees().contains("ClasseCobayeAnnotee"),
            resultat.classesIgnorees().toString());
    }

    @Test
    void leResultat_refuseUneComposanteNulle() {
        // Un scan sans classe ignorée se dit avec une liste vide, jamais avec null : c'est ce
        // qui permet à l'appelant de lire le résultat sans le tester.
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

        // Le refus doit dire quel chemin a été lu : c'est presque toujours une racine mal
        // calculée, et le message est le seul endroit où l'utilisateur peut s'en apercevoir.
        assertTrue(e.getMessage().contains("un-dossier-qui-n-existe-pas"), e.getMessage());
    }

    // @TempDir : JUnit crée un répertoire temporaire avant le test et le supprime après. Rien à
    // nettoyer, et deux exécutions ne peuvent pas se gêner.
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

    // --- L'avertissement des boîtes perdues à la lecture ---
    //
    // Il ressemble à celui des classes illisibles et ne dit pas la même chose : celui-là vaut pour
    // un .class que le chargeur a refusé, celui-ci pour une classe chargée dont la lecture a buté
    // sur un type absent. Qu'il soit dit au bon moment est éprouvé de bout en bout par
    // ProjectScannerIntegrationTest ; ici, son texte.

    // Ce qu'une méthode de signalement écrit, plutôt que ce qu'elle rend.
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
        // Le reproche de l'autre message n'a pas lieu d'être ici : la classe a chargé.
        assertFalse(dit.contains("only readable on a loaded class"), dit);
    }

    @Test
    void tropDeBoitesPerdues_sontCompteesEtNonNommees() {
        // Au-delà du seuil, une liste de noms noierait le message qui la porte. C'est le même
        // seuil que pour les classes illisibles, donc la même règle.
        ImmutableList<String> trop =
            IntStream.rangeClosed(0, ProjectScanner.SEUIL_ENUMERATION)
                .mapToObj(i -> "com.exemple.C" + i)
                .collect(ImmutableList.toImmutableList());

        String dit = ditPar(() -> ProjectScanner.signalerBoitesPerdues(trop));

        assertTrue(dit.startsWith(trop.size() + " class(es) loaded but could not be read"), dit);
        assertFalse(dit.contains("com.exemple.C0"), dit);
    }
}
