package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.Avertissements;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class OrdreDuFichierClasseTest {

    static class Reservoir {
        static final long GRAND = 4_294_967_296L;
        static final double MOITIE = 0.5d;
        static final float TIERS = 0.33f;
        static final int COMPTE = 70_000;
        static final String NOM = "reservoir";

        static Supplier<String> nommeur() {
            return () -> NOM + GRAND;
        }
    }

    @Test
    void unLongEtUneLambda_neDecalentPasLaLecture() {
        ImmutableList<String> champs = OrdreDuFichierClasse.champs(Reservoir.class);

        assertEquals(List.of("GRAND", "MOITIE", "TIERS", "COMPTE", "NOM"), champs);
    }

    @Test
    void lesMethodesDUnFichierAConstantesLongues_sontLuesDansLOrdre() {
        ImmutableList<String> methodes = OrdreDuFichierClasse.methodes(Reservoir.class);

        assertEquals("<init>()V", methodes.get(0));
        assertEquals("nommeur()Ljava/util/function/Supplier;", methodes.get(1));
        assertTrue(methodes.stream().anyMatch(m -> m.startsWith("lambda$")), methodes::toString);
    }


    static class Signatures {
        public void rien() {}

        public boolean tout(byte b, char c, short s, int i, long l, float f, double d) {
            return true;
        }

        public String[] tableaux(int[] entiers) {
            return entiers.length == 0 ? null : new String[0];
        }
    }

    private static String cleDe(String nom) {
        Method methode = Arrays.stream(Signatures.class.getDeclaredMethods())
            .filter(m -> m.getName().equals(nom)).findFirst().orElseThrow();
        return OrdreDuFichierClasse.cle(methode);
    }

    @Test
    void lesPrimitifs_serventLeurLettreJvm() {
        assertEquals("rien()V", cleDe("rien"));
        assertEquals("tout(BCSIJFD)Z", cleDe("tout"));
    }

    @Test
    void lesTableaux_gardentLeurCrochet() {
        assertEquals("tableaux([I)[Ljava/lang/String;", cleDe("tableaux"));
    }

    @Test
    void laCleCalculee_estCelleLueDansLeFichier() {
        ImmutableList<String> lues = OrdreDuFichierClasse.methodes(Signatures.class);

        for (Method methode : Signatures.class.getDeclaredMethods()) {
            if (methode.isSynthetic()) {
                continue;
            }
            assertTrue(lues.contains(OrdreDuFichierClasse.cle(methode)),
                () -> OrdreDuFichierClasse.cle(methode) + " absente de " + lues);
        }
    }


    @Test
    void uneClasseSansFichier_estRendueNulle() {
        Runnable lambda = () -> {};

        assertNull(OrdreDuFichierClasse.champs(lambda.getClass()));
        assertNull(OrdreDuFichierClasse.methodes(lambda.getClass()));
    }

    @Test
    void ordrePerdu_neSeDitQuUneFoisParClasse() {
        Runnable lambda = () -> {};
        Regles regles = Regles.lues("{ \"order\": \"declaration\" }", "umlg.json");
        List<String> dits = new ArrayList<>();

        try (Avertissements.Canal canal = Avertissements.redirectTo(dits::add)) {
            MembresOrdonnes.champs(lambda.getClass(), regles);
            MembresOrdonnes.methodes(lambda.getClass(), regles);
            MembresOrdonnes.champs(lambda.getClass(), regles);
        }

        assertEquals(1, dits.size(), dits::toString);
        assertTrue(dits.get(0).contains("\"order\": \"declaration\""), dits.get(0));
        assertTrue(dits.get(0).contains("alphabetical order"), dits.get(0));
    }
}
