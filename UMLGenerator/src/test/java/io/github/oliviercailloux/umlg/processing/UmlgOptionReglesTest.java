package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// L'option -Aumlg.rules : ce que le processeur retrouve quand on lui donne le fichier de règles.
//
// CE QUE CES TESTS PROTÈGENT
//
// Le défaut de @UMLClass est PROJECT, c'est-à-dire « la stratégie que déclare umlg.json ». Ce
// fichier vit à la racine des classes compilées, et javac ne sait pas où il se trouve : sans
// l'option, le processeur ne peut pas dire si un membre sera dessiné, et se tait sur tout ce qui
// en dépend. L'option lui rend la vue, et c'est la seule chose que la bibliothèque taise.
//
// Chaque test compare donc les deux mondes : sans l'option, silence ; avec, le même avertissement
// que si la classe avait déclaré sa stratégie en toutes lettres.
class UmlgOptionReglesTest {

    // Une classe qui s'en remet au projet, et un @UMLInclude sur un membre public. Utile ou non
    // selon ce que le fichier déclare : c'est tout le cas d'espèce.
    private static final String COBAYE = """
        @UMLClass
        class Client {
            @UMLInclude
            public String nom;
        }
        """;

    private static Path ecrireRegles(Path tmp, String contenu) throws IOException {
        Path fichier = tmp.resolve("umlg.json");
        Files.writeString(fichier, contenu, StandardCharsets.UTF_8);
        return fichier;
    }

    private static Verdict compiler(Path tmp, List<String> options) throws IOException {
        return CompilationCobaye.compilerCorps(tmp, COBAYE, options);
    }

    @Test
    void sansLOption_leProcesseurSeTait(@TempDir Path tmp) throws IOException {
        // Le comportement par défaut : indécidable, donc muet. Rien n'est faux pour autant.
        Verdict verdict = compiler(tmp, List.of());

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void avecLOption_lAvertissementRevient(@TempDir Path tmp) throws IOException {
        // « "strategy": "public" » affiche déjà l'attribut : le @UMLInclude ne déroge à rien, et le
        // processeur peut enfin le dire.
        Path regles = ecrireRegles(tmp, "{ \"strategy\": \"public\" }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("@UMLInclude has no effect"), verdict.rapport());
        assertTrue(verdict.avertissementContient("PUBLIC"),
            "Le message doit nommer la stratégie réellement appliquée : " + verdict.rapport());
    }

    @Test
    void avecUnProjetQuiNAfficheRien_rienNestSignale(@TempDir Path tmp) throws IOException {
        // L'autre moitié, celle qui compte autant : le @UMLInclude est ici indispensable, et le
        // processeur doit se taire. Un avertissement serait un contresens.
        Path regles = ecrireRegles(tmp, "{ \"strategy\": \"none\" }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void unMotifQuiMasqueLeChamp_neReprochePasLInclude(@TempDir Path tmp) throws IOException {
        // Le cœur de la question : « strategy public » afficherait ce champ, mais « hide nom »
        // l'écarte, et le @UMLInclude est alors le seul moyen de le garder. Le suivre — « remove
        // it » — effaçait la ligne du diagramme.
        Path regles = ecrireRegles(tmp, "{ \"strategy\": \"public\", \"hide\": [\"nom\"] }");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void surUneAssociationDansLaBoite_lAvertissementDependAussiDesRegles(@TempDir Path tmp)
        throws IOException {
        // « Déjà montré dans le compartiment des attributs, quelle que soit la stratégie » : vrai
        // de la stratégie, faux du filtre anti-bruit, qui écarte aussi cette ligne-là. Avec les
        // règles, et elles seules, le processeur peut conclure.
        String corps = """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLInclude
                @UMLAssociation(target = Commande.class, notation = UMLNotation.ATTRIBUTE)
                private Commande commande;
            }
            """;
        Path regles = ecrireRegles(tmp, "{ \"strategy\": \"public\" }");

        Verdict avecRegles = CompilationCobaye.compilerCorps(tmp, corps,
            List.of("-Aumlg.rules=" + regles));

        assertTrue(avecRegles.reussie(), avecRegles.rapport());
        assertTrue(avecRegles.avertissementContient("already shown in the attribute compartment"),
            avecRegles.rapport());

        // Et le motif qui masque ce champ referme la bouche du processeur.
        Path masquant = ecrireRegles(tmp, "{ \"strategy\": \"public\", \"hide\": [\"commande\"] }");

        Verdict masque = CompilationCobaye.compilerCorps(tmp, corps,
            List.of("-Aumlg.rules=" + masquant));

        assertTrue(masque.reussie(), masque.rapport());
        assertTrue(masque.avertissements().isEmpty(), masque.rapport());
    }

    @Test
    void lExcludeSansEffet_parleSansLesRegles(@TempDir Path tmp) throws IOException {
        // L'avertissement de sens inverse n'a pas le même besoin : un motif ne peut que masquer
        // davantage, si bien qu'un membre déjà masqué par la stratégie l'est à coup sûr. Le
        // resserrement ne doit donc pas déborder sur lui.
        Verdict verdict = CompilationCobaye.compilerCorps(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLExclude
                private String motDePasse;
            }
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("@UMLExclude has no effect"), verdict.rapport());
    }

    @Test
    void fichierIntrouvable_estSignaleSansArreterLaCompilation(@TempDir Path tmp)
        throws IOException {
        // L'utilisateur a nommé un fichier qu'il n'a pas écrit : son code reste juste, mais il
        // attend des vérifications qu'il n'aura pas. Un avertissement, donc, et non une erreur.
        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + tmp.resolve("absent.json")));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("no such file"), verdict.rapport());
    }

    @Test
    void cheminRelatifIntrouvable_ditOuIlAChercher(@TempDir Path tmp) throws IOException {
        // Un chemin relatif est résolu contre le répertoire de travail de javac, qui n'est pas
        // celui du projet dès que la construction est lancée d'ailleurs. Le message se contentait
        // de répéter ce que l'utilisateur avait écrit, ce qui ne lui apprenait rien : il cherchait
        // un fichier qui était pourtant bien là.
        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=regles/umlg.json"));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("looked for "), verdict.rapport());
        assertTrue(verdict.avertissementContient(
            Path.of("regles/umlg.json").toAbsolutePath().toString()), verdict.rapport());
        assertTrue(verdict.avertissementContient("working directory"), verdict.rapport());
    }

    @Test
    void nowarn_neFaitPasTaireLAvertissementDeConfiguration(@TempDir Path tmp) throws IOException {
        // Faire taire les avertissements veut dire « ne me dis pas ce que tu as trouvé dans mon
        // code » ; cela ne peut pas vouloir dire « ne me dis pas que tu es mal configuré ». Le
        // contournement d'-Aumlg.warnings=none ne servait pourtant à rien, faute d'être écrit
        // dans la gravité que -nowarn respecte — et Maven passe -nowarn par défaut, si bien que
        // le cas courant était le cas muet.
        Verdict verdict = CompilationCobaye.compilerCorps(tmp, """
            @UMLClass(stereotype = "<<Service>>")
            class Paiement {}
            """, List.of("-nowarn", "-Aumlg.rules=" + tmp.resolve("absent.json")));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("no such file"), verdict.rapport());
        // Le témoin : l'avertissement qui porte sur le code, lui, se tait bel et bien.
        assertTrue(verdict.avertissements().stream()
            .noneMatch(message -> message.contains("already contains the guillemets")),
            "-nowarn doit taire ce que le processeur a trouvé dans le code : " + verdict.rapport());
    }

    @Test
    void fichierFautif_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        // Le même fichier arrêterait la génération pour la même raison : le dire ici le dit plus
        // tôt, ce qui est tout l'intérêt du processeur.
        Path regles = ecrireRegles(tmp, "{ \"couleur\": { \"Client\": \"rouge\" } }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + regles));

        assertFalse(verdict.reussie(), "Un fichier de règles fautif ne décrit aucun diagramme.");
        assertTrue(verdict.erreurContient("unknown rule"), verdict.rapport());
    }

    @Test
    void avertissementsEteints_taisentAussiCeuxQueLOptionRend(@TempDir Path tmp)
        throws IOException {
        // -Aumlg.warnings=none garde son sens : il éteint tout, y compris ce que l'option vient
        // de rendre audible. Les erreurs, elles, ne s'éteignent jamais.
        Path regles = ecrireRegles(tmp, "{ \"strategy\": \"public\" }\n");

        Verdict verdict =
            compiler(tmp, List.of("-Aumlg.rules=" + regles, "-Aumlg.warnings=none"));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }
}
