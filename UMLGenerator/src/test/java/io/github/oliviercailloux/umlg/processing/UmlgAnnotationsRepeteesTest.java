package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Un diagnostic sur une annotation répétée dit laquelle.
//
// CE QUE CES TESTS PROTÈGENT
//
// Quand l'utilisateur écrit plusieurs @UMLNote sur la même classe, javac les emballe dans un
// conteneur qu'il fabrique lui-même — @UMLNotes — et qui n'existe nulle part dans le fichier
// source. Ce conteneur n'a donc aucune position : le diagnostic se pose sur la déclaration de
// classe entière, et sur cinq notes dont une seule est fautive, rien ne dit laquelle. L'API des
// processeurs ne donne pas de quoi retrouver la place perdue.
//
// Le texte est le seul endroit qui reste pour la dire. Le rang n'est écrit que lorsqu'il y en a
// plusieurs : sur une annotation seule, javac sait parfaitement la situer, et « the 1st » ne
// serait que du bruit — c'est la moitié de ce que ces tests vérifient.
class UmlgAnnotationsRepeteesTest {

    // L'en-tête que partagent tous les cobayes vit dans CompilationCobaye, avec le harnais.

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
        // Le suffixe ordinal n'est pas « nd » pour tout le monde : la règle anglaise se vérifie
        // au-delà du deuxième.
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
        // Elle a perdu sa position comme les autres, dès lors qu'elle est emballée : le rang lui
        // est donc dû, « the 1st » compris.
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
        // « 11st » n'existe pas : la règle anglaise fait exception de onze, douze et treize, et
        // un message que l'utilisateur lit n'a pas le droit d'écorcher sa propre langue.
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
        // Elle est parfaitement située par javac : un rang n'apprendrait rien, et alourdirait
        // tous les messages du cas courant.
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
