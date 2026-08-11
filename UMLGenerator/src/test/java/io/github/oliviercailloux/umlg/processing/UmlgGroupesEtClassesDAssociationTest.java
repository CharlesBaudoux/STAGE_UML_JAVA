package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Ce que le processeur dit de @UMLPackage et de @UMLAssociationClass.
class UmlgGroupesEtClassesDAssociationTest {

    // L'en-tête que partagent tous les cobayes vit dans CompilationCobaye, avec le harnais.

    private static Verdict compiler(Path tmp, String corps) throws IOException {
        return CompilationCobaye.compilerCorps(tmp, corps);
    }

    // --- Le cas nominal ---

    @Test
    void codeCorrect_compileSansRienSignaler(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLPackage(name = "Facturation", color = "#EEEEEE")
            class Facture {}

            @UMLClass
            @UMLPackage(name = "Facturation", color = "#EEEEEE")
            class Ligne {}

            @UMLClass
            class Moteur {}

            @UMLClass
            @UMLAssociationClass(source = Facture.class, target = Moteur.class)
            class Rattachement {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    // --- @UMLPackage ---

    @Test
    void groupeSurClasseNonAnnotee_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLPackage(name = "Facturation")
            class HorsDiagramme {}
            """);

        assertFalse(verdict.reussie(), "Il n'y a pas de boîte à ranger.");
        assertTrue(verdict.erreurContient("does not carry @UMLClass"), verdict.rapport());
    }

    @Test
    void nomDeGroupeBlanc_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLPackage(name = "   ")
            class Facture {}
            """);

        assertFalse(verdict.reussie(), "Un groupe est identifié par son nom.");
        assertTrue(verdict.erreurContient("name()"), verdict.rapport());
    }

    @Test
    void couleurDeGroupeInconnue_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLPackage(name = "Facturation", color = "PasUneCouleur")
            class Facture {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        // Le message est celui de ColorCodec, qui interroge le catalogue réel du moteur : la
        // question posée est « PlantUML connaît-il cette couleur ? », et non « en a-t-elle la
        // forme ? » — « PasUneCouleur » en a parfaitement la forme.
        assertTrue(verdict.erreurContient("unknown to PlantUML"), verdict.rapport());
    }

    @Test
    void deuxCouleursPourUnGroupe_sontSignalees(@TempDir Path tmp) throws IOException {
        // Un rectangle n'a qu'une couleur : l'auteur a écrit la seconde pour rien.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLPackage(name = "Facturation", color = "#EEEEEE")
            class Facture {}

            @UMLClass
            @UMLPackage(name = "Facturation", color = "#123456")
            class Ligne {}
            """);

        assertTrue(verdict.reussie(), "Le diagramme reste juste, seule la couleur change.");
        assertTrue(verdict.avertissementContient("already coloured"), verdict.rapport());
    }

    // --- @UMLAssociationClass ---

    @Test
    void extremiteHorsDiagramme_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Etudiant {}

            class Cours {}

            @UMLClass
            @UMLAssociationClass(source = Etudiant.class, target = Cours.class)
            class Inscription {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("does not carry @UMLClass"), verdict.rapport());
    }

    @Test
    void classeDAssociationEnExtremiteDElleMeme_arreteLaCompilation(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Cours {}

            @UMLClass
            @UMLAssociationClass(source = Inscription.class, target = Cours.class)
            class Inscription {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("is the association class itself"), verdict.rapport());
        // Le message nomme l'extrémité fautive, et il est celui d'AssociationClassExtractor : la
        // règle n'existe plus qu'à un endroit, et les deux moitiés la contrôlent dans le même
        // ordre — la réflexivité avant l'appartenance au diagramme.
        assertTrue(verdict.erreurContient("source() is the association class itself"),
            verdict.rapport());
        assertTrue(verdict.erreurContient("cannot be one of the two ends"), verdict.rapport());
    }

    @Test
    void classeDAssociationNonAnnotee_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Etudiant {}

            @UMLClass
            class Cours {}

            @UMLAssociationClass(source = Etudiant.class, target = Cours.class)
            class Inscription {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("does not carry @UMLClass"), verdict.rapport());
    }

    @Test
    void lesDeuxExtremitesFautives_sontSignaleesEnsemble(@TempDir Path tmp) throws IOException {
        // Recompiler pour découvrir la seconde faute serait une perte de temps évitable.
        Verdict verdict = compiler(tmp, """
            class Etudiant {}

            class Cours {}

            @UMLClass
            @UMLAssociationClass(source = Etudiant.class, target = Cours.class)
            class Inscription {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurs().size() >= 2,
            "Les deux extrémités doivent être nommées : " + verdict.rapport());
    }
}
