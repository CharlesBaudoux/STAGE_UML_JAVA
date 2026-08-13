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

// La lecture du fichier .class : le réservoir de constantes, les descripteurs, et l'échec.
//
// Ce que ces tests protègent tient en une phrase : le réservoir de constantes se traverse à
// l'aveugle, et une entrée mal dimensionnée décale tout ce qui suit. Le décalage ne se voit pas —
// il ne lève rien, il rend simplement des membres rangés dans un ordre absurde, ou un fichier
// déclaré illisible. D'où des cobayes choisis pour la forme de leur réservoir plutôt que pour ce
// qu'ils font.
//
// Les cobayes ne portent pas @UMLClass : ils n'ont rien à faire sur un diagramme, et les tests
// d'intégration balaient tout target/test-classes.
class OrdreDuFichierClasseTest {

    // Un cobaye par nature de constante délicate.
    //
    // Un long et un double occupent DEUX entrées du réservoir : la seconde n'existe pas, et le
    // lecteur doit sauter l'indice. Une lambda, elle, s'écrit en invokedynamic, ce qui force un
    // CONSTANT_MethodHandle — trois octets, ni deux ni quatre. Ces trois natures ne paraissent
    // dans aucun autre cobaye de la suite : sans celui-ci, le lecteur n'était jamais éprouvé là
    // où il peut se décaler.
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
        // Si le long ou le double avaient été comptés pour une seule entrée, ou le
        // MethodHandle pour deux octets, la suite du fichier serait lue de travers : la lecture
        // échouerait (null) ou rendrait des noms qui ne sont pas ceux-là.
        ImmutableList<String> champs = OrdreDuFichierClasse.champs(Reservoir.class);

        assertEquals(List.of("GRAND", "MOITIE", "TIERS", "COMPTE", "NOM"), champs);
    }

    @Test
    void lesMethodesDUnFichierAConstantesLongues_sontLuesDansLOrdre() {
        // La table des méthodes vient après le réservoir : elle n'est atteinte correctement que
        // si chaque entrée a été sautée de la bonne longueur. La lambda ajoute ici une méthode
        // synthétique, que la lecture rend comme les autres — c'est l'appelant qui l'écarte.
        ImmutableList<String> methodes = OrdreDuFichierClasse.methodes(Reservoir.class);

        // Le constructeur ouvre la table, avant les méthodes écrites ; le corps de la lambda la
        // ferme, sous le nom que javac lui donne. La réflexion écarte ces deux-là plus tard —
        // ici, c'est bien tout ce que le fichier contient qui est lu.
        assertEquals("<init>()V", methodes.get(0));
        assertEquals("nommeur()Ljava/util/function/Supplier;", methodes.get(1));
        assertTrue(methodes.stream().anyMatch(m -> m.startsWith("lambda$")), methodes::toString);
    }

    // --- Les descripteurs, l'autre moitié de la correspondance ---

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
        // La clé calculée depuis la réflexion doit s'écrire exactement comme celle lue dans le
        // fichier, sans quoi aucun membre ne se retrouve et la boîte entière part en fin de liste.
        assertEquals("rien()V", cleDe("rien"));
        assertEquals("tout(BCSIJFD)Z", cleDe("tout"));
    }

    @Test
    void lesTableaux_gardentLeurCrochet() {
        assertEquals("tableaux([I)[Ljava/lang/String;", cleDe("tableaux"));
    }

    @Test
    void laCleCalculee_estCelleLueDansLeFichier() {
        // Le vrai contrat : les deux moitiés se rejoignent. Le comparer sur les vraies méthodes
        // vaut mieux que de figer une chaîne écrite à la main des deux côtés.
        ImmutableList<String> lues = OrdreDuFichierClasse.methodes(Signatures.class);

        // isSynthetic écarte ce que l'instrumentation de couverture ajoute à la classe chargée
        // ($jacocoInit) : ce que la réflexion voit alors n'est pas ce que le fichier contient.
        for (Method methode : Signatures.class.getDeclaredMethods()) {
            if (methode.isSynthetic()) {
                continue;
            }
            assertTrue(lues.contains(OrdreDuFichierClasse.cle(methode)),
                () -> OrdreDuFichierClasse.cle(methode) + " absente de " + lues);
        }
    }

    // --- Le fichier introuvable ---

    @Test
    void uneClasseSansFichier_estRendueNulle() {
        // La classe d'une lambda est fabriquée à l'exécution : elle n'a aucun fichier .class à
        // relire. C'est le seul échec de lecture qu'un test puisse produire sans truquer un
        // fichier, et c'est celui qui compte — la génération continue, en alphabet.
        Runnable lambda = () -> {};

        assertNull(OrdreDuFichierClasse.champs(lambda.getClass()));
        assertNull(OrdreDuFichierClasse.methodes(lambda.getClass()));
    }

    @Test
    void ordrePerdu_neSeDitQuUneFoisParClasse() {
        // Le fichier est demandé pour les attributs, pour les méthodes, pour les liens et pour
        // les bulles. Le même constat répété quatre fois n'apprend rien de plus la deuxième fois,
        // et noie les autres avertissements.
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
