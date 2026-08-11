package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// Où vont les avertissements, et ce qu'il advient du canal quand on l'a posé.
//
// LE TROU QUE CES TESTS FERMENT
//
// versLeCanal et son pendant n'étaient appelés par aucun test : les deux seuls endroits qui
// relisaient un avertissement détournaient System.err à la place. Personne ne vérifiait donc que
// le canal se défait — et un canal qui ne se défait pas capte la génération d'après, sur un fil
// qu'un pool réutilise. C'est le pire des symptômes : les avertissements d'un module Maven
// atterrissent dans le log du module suivant.
class AvertissementsTest {

    @Test
    void unCanalPose_recoitLesAvertissementsDuFil() {
        List<String> recus = new ArrayList<>();

        try (Avertissements.Canal canal = Avertissements.versLeCanal(recus::add)) {
            Avertissements.dire("la vue est illisible");
        }

        assertEquals(List.of("la vue est illisible"), recus);
    }

    @Test
    void unCanalFerme_neRecoitPlusRien() {
        List<String> recus = new ArrayList<>();

        try (Avertissements.Canal canal = Avertissements.versLeCanal(recus::add)) {
            Avertissements.dire("pendant");
        }
        String surLaSortieDErreur = enCaptantLaSortieDErreur(() -> Avertissements.dire("après"));

        assertEquals(List.of("pendant"), recus, "Le canal fermé ne doit plus rien capter.");
        assertTrue(surLaSortieDErreur.contains("après"),
            "Le fil doit avoir retrouvé la sortie d'erreur : " + surLaSortieDErreur);
    }

    @Test
    void deuxCanauxEmboites_seDefontDansLOrdreInverse() {
        // C'est ce dont generate() a besoin : il pose le sien par-dessus celui du plugin Maven, et
        // sa fermeture doit rendre celui du plugin, non la sortie d'erreur.
        List<String> dehors = new ArrayList<>();
        List<String> dedans = new ArrayList<>();

        try (Avertissements.Canal externe = Avertissements.versLeCanal(dehors::add)) {
            try (Avertissements.Canal interne = Avertissements.versLeCanal(dedans::add)) {
                Avertissements.dire("dedans");
            }
            Avertissements.dire("dehors");
        }

        assertEquals(List.of("dedans"), dedans);
        assertEquals(List.of("dehors"), dehors, "Le canal externe doit être rendu, pas perdu.");
    }

    @Test
    void unLecteurAjoute_neFaitPasTaireLeCanalEnPlace() {
        // La forme qu'emploie generate() : les avertissements entrent dans le compte rendu et
        // continuent d'atteindre le relais temps réel. Remplacer purement et simplement ferait
        // taire le log de Maven pendant toute la génération, c'est-à-dire pendant le seul moment
        // où il a quelque chose à écrire.
        List<String> relais = new ArrayList<>();
        List<String> lus = new ArrayList<>();

        try (Avertissements.Canal externe = Avertissements.versLeCanal(relais::add)) {
            try (Avertissements.Canal ajout = Avertissements.enPlusDuCanalCourant(lus::add)) {
                Avertissements.dire("les deux");
            }
        }

        assertEquals(List.of("les deux"), relais);
        assertEquals(List.of("les deux"), lus);
    }

    @Test
    void sansAucunCanal_lAvertissementVaSurLaSortieDErreurPrefixe() {
        // Le défaut, celui d'un programme qu'on lance à la main.
        String ecrit = enCaptantLaSortieDErreur(() -> Avertissements.dire("rien de posé"));

        assertEquals("Warning: rien de posé", ecrit.strip());
    }

    @Test
    void unLecteurAjouteSansCanal_ecritQuandMemeSurLaSortieDErreur() {
        // Le cas du programme lancé à la main : generate() ajoute son lecteur au canal par défaut,
        // et l'utilisateur doit continuer de voir passer ses avertissements.
        List<String> lus = new ArrayList<>();

        String ecrit = enCaptantLaSortieDErreur(() -> {
            try (Avertissements.Canal ajout = Avertissements.enPlusDuCanalCourant(lus::add)) {
                Avertissements.dire("vu deux fois");
            }
        });

        assertEquals(List.of("vu deux fois"), lus);
        assertTrue(ecrit.contains("vu deux fois"), ecrit);
    }

    @Test
    void unCanalNull_estRefuse() {
        assertThrows(NullPointerException.class, () -> Avertissements.versLeCanal(null));
    }

    @Test
    void unMessageNull_estRefuse() {
        assertThrows(NullPointerException.class, () -> Avertissements.dire(null));
    }

    // Ce que la sortie d'erreur reçoit pendant l'exécution de l'action. Le flux d'origine est
    // rendu dans tous les cas : le perdre rendrait muettes toutes les erreurs de la suite.
    private static String enCaptantLaSortieDErreur(Runnable action) {
        PrintStream origine = System.err;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capture, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setErr(origine);
        }
        return capture.toString(StandardCharsets.UTF_8);
    }
}
