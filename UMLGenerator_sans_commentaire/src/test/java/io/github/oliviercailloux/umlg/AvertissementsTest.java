package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.warning.Avertisseur;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvertissementsTest {

    @Test
    void unCanalPose_recoitLesAvertissementsDuFil() {
        List<String> recus = new ArrayList<>();

        try (Avertissements.Canal canal = Avertissements.versLeCanal(recus::add)) {
            Avertisseur.dire("la vue est illisible");
        }

        assertEquals(List.of("la vue est illisible"), recus);
    }

    @Test
    void unCanalFerme_neRecoitPlusRien() {
        List<String> recus = new ArrayList<>();

        try (Avertissements.Canal canal = Avertissements.versLeCanal(recus::add)) {
            Avertisseur.dire("pendant");
        }
        String surLaSortieDErreur = enCaptantLaSortieDErreur(() -> Avertisseur.dire("après"));

        assertEquals(List.of("pendant"), recus, "Le canal fermé ne doit plus rien capter.");
        assertTrue(surLaSortieDErreur.contains("après"),
            "Le fil doit avoir retrouvé la sortie d'erreur : " + surLaSortieDErreur);
    }

    @Test
    void deuxCanauxEmboites_seDefontDansLOrdreInverse() {
        List<String> dehors = new ArrayList<>();
        List<String> dedans = new ArrayList<>();

        try (Avertissements.Canal externe = Avertissements.versLeCanal(dehors::add)) {
            try (Avertissements.Canal interne = Avertissements.versLeCanal(dedans::add)) {
                Avertisseur.dire("dedans");
            }
            Avertisseur.dire("dehors");
        }

        assertEquals(List.of("dedans"), dedans);
        assertEquals(List.of("dehors"), dehors, "Le canal externe doit être rendu, pas perdu.");
    }

    @Test
    void unLecteurAjoute_neFaitPasTaireLeCanalEnPlace() {
        List<String> relais = new ArrayList<>();
        List<String> lus = new ArrayList<>();

        try (Avertissements.Canal externe = Avertissements.versLeCanal(relais::add)) {
            try (Avertissements.Canal ajout = Avertisseur.enPlusDuCanalCourant(lus::add)) {
                Avertisseur.dire("les deux");
            }
        }

        assertEquals(List.of("les deux"), relais);
        assertEquals(List.of("les deux"), lus);
    }

    @Test
    void sansAucunCanal_lAvertissementVaSurLaSortieDErreurPrefixe() {
        String ecrit = enCaptantLaSortieDErreur(() -> Avertisseur.dire("rien de posé"));

        assertEquals("Warning: rien de posé", ecrit.strip());
    }

    @Test
    void unLecteurAjouteSansCanal_ecritQuandMemeSurLaSortieDErreur() {
        List<String> lus = new ArrayList<>();

        String ecrit = enCaptantLaSortieDErreur(() -> {
            try (Avertissements.Canal ajout = Avertisseur.enPlusDuCanalCourant(lus::add)) {
                Avertisseur.dire("vu deux fois");
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
        assertThrows(NullPointerException.class, () -> Avertisseur.dire(null));
    }

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
