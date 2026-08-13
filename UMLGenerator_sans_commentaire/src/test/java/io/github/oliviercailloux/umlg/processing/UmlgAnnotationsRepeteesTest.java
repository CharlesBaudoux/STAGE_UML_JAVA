package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgAnnotationsRepeteesTest {


    private static Verdict compiler(Path tmp, String corps) throws IOException {
        return CompilationCobaye.compilerCorps(tmp, corps);
    }

    @Test
    void laSecondeNoteFautive_estDesigneeParSonRang(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLNote(text = "Coeur du domaine")
            @UMLNote(text = "   ")
            @UMLNote(text = "Relire le contrat")
            class Compte {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("the 2nd @UMLNote on Compte"), verdict.rapport());
    }

    @Test
    void laTroisiemeNoteFautive_estDesigneeParSonRang(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLNote(text = "Une")
            @UMLNote(text = "Deux")
            @UMLNote(text = " ")
            class Compte {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("the 3rd @UMLNote on Compte"), verdict.rapport());
    }

    @Test
    void laPremiereDePlusieurs_estDesigneeParSonRang(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLNote(text = " ")
            @UMLNote(text = "Relire le contrat")
            class Compte {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("the 1st @UMLNote on Compte"), verdict.rapport());
    }

    @Test
    void auDelaDeLaDixieme_leSuffixeAnglaisResteJuste(@TempDir Path tmp) throws IOException {
        StringBuilder cobaye = new StringBuilder("@UMLClass\n");
        for (int rang = 1; rang <= 10; rang++) {
            cobaye.append("@UMLNote(text = \"note ").append(rang).append("\")\n");
        }
        cobaye.append("@UMLNote(text = \"  \")\nclass Compte {}\n");

        Verdict verdict = compiler(tmp, cobaye.toString());

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("the 11th @UMLNote on Compte"), verdict.rapport());
    }

    @Test
    void uneNoteSeule_neSeVoitPasAffecterDeRang(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLNote(text = "  ")
            class Compte {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("@UMLNote on Compte"), verdict.rapport());
        assertTrue(verdict.erreurs().stream().noneMatch(message -> message.contains("the 1st")),
            verdict.rapport());
    }

    @Test
    void laSecondeContrainteFautive_estDesigneeParSonRang(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLConstraint(rule = "titulaire majeur")
            @UMLConstraint(rule = "{solde >= 0}")
            class Compte {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("the 2nd @UMLConstraint on Compte"),
            verdict.rapport());
    }

    @Test
    void laSecondeDependanceFautive_estDesigneeParSonRang(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Service {}

            @UMLClass
            @UMLDependency(target = Service.class)
            @UMLDependency(target = Client.class)
            class Client {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("the 2nd @UMLDependency on Client"), verdict.rapport());
        assertTrue(verdict.erreurContient("cannot depend on itself"), verdict.rapport());
    }
}
