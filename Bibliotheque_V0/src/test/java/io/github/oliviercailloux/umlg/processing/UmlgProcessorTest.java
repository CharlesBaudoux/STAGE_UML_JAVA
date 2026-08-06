package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Ce que le processeur refuse de laisser compiler.
//
// Chaque test compile un vrai fichier avec un vrai javac : ce qui est mesuré est donc exactement
// ce que l'utilisateur obtiendrait dans son terminal, et non une simulation.
//
// Les cobayes sont écrits ici en chaînes de caractères, et non en classes imbriquées comme
// ailleurs dans la suite : des classes fautives ne compileraient pas, et le module de test avec
// elles. C'est le prix, assumé, d'un processeur qui fait bien son travail.
class UmlgProcessorTest {

    // Tous les cobayes partagent cet en-tête ; l'écrire une fois évite de noyer la faute testée.
    private static final String ENTETE = """
        import io.github.oliviercailloux.umlg.annotation.*;
        import java.util.List;
        """;

    private static Verdict compiler(Path tmp, String corps) throws IOException {
        return CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + corps);
    }

    // --- Le cas nominal : un code correct doit passer ---

    @Test
    void codeCorrect_compileSansErreur(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC, color = "LightBlue", stereotype = "Entity")
            class Commande {}

            @UMLClass
            class ServiceBancaire {}

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            @UMLDependency(target = ServiceBancaire.class, label = "facture via")
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*",
                    sourceCardinality = "1", targetRole = "achats")
                private List<Commande> commandes;

                @UMLInclude
                private int compteur;
            }
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        // Un code correct est un code muet : pas seulement sans erreur, mais sans rien à
        // signaler. C'est ce qui garde les avertissements audibles quand il y en a un.
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    // --- La multiplicité d'un membre ---

    @Test
    void multipliciteIllisible_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Mnemonique {
                @UMLMultiplicity("beaucoup")
                private String alias;
            }
            """);

        assertFalse(verdict.reussie(), "\"beaucoup\" n'est pas une multiplicité.");
        assertTrue(verdict.erreurContient("valid UML multiplicity"), verdict.rapport());
    }

    @Test
    void multipliciteEtAssociationSurLeMemeAttribut_arretentLaCompilation(@TempDir Path tmp)
        throws IOException {
        // Deux multiplicités sur la même propriété : la bibliothèque saurait en choisir une, mais
        // le faire en silence reviendrait à décider à la place de l'auteur.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLMultiplicity("0..*")
                @UMLAssociation(target = Commande.class, targetCardinality = "1..*")
                private List<Commande> commandes;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("already given by targetCardinality"), verdict.rapport());
    }

    @Test
    void multipliciteSurUneClasseHorsDiagramme_arreteLaCompilation(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            class Mnemonique {
                @UMLMultiplicity("0..*")
                private String alias;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("does not carry @UMLClass"), verdict.rapport());
    }

    // --- La cible d'une relation ---

    @Test
    void cibleSansUmlClass_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class)
                private Commande commande;
            }
            """);

        assertFalse(verdict.reussie(), "La cible n'est pas dans le diagramme.");
        assertTrue(verdict.erreurContient("does not carry @UMLClass"), verdict.rapport());
        // Le message doit dire quoi faire, pas seulement ce qui ne va pas.
        assertTrue(verdict.erreurContient("Annotate it"), verdict.rapport());
    }

    @Test
    void ciblePrimitive_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        // int.class est du Java légal : son type est Class<Integer>. Seul le processeur peut
        // refuser ce qui, pour javac seul, est une expression parfaitement valide.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLAssociation(target = int.class)
                private int age;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("primitive type"), verdict.rapport());
    }

    // --- Les multiplicités ---

    @Test
    void multipliciteInversee_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "3..1")
                private Commande commande;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("lower bound exceeds the upper bound"),
            verdict.rapport());
    }

    @Test
    void multipliciteIncomprehensible_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, sourceCardinality = "beaucoup")
                private Commande commande;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("valid UML multiplicity"), verdict.rapport());
        // Le message vient du modèle : il nomme le paramètre fautif, ce que le positionnement
        // sur l'annotation entière ne dirait pas.
        assertTrue(verdict.erreurContient("sourceCardinality"), verdict.rapport());
    }

    // --- La couleur ---

    @Test
    void couleurInconnueDePlantUml_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        // La forme est valide — c'est un mot —, seule l'interrogation du vrai catalogue de
        // PlantUML peut la refuser.
        Verdict verdict = compiler(tmp, """
            @UMLClass(color = "PasUneCouleur")
            class Client {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("Color unknown to PlantUML"), verdict.rapport());
    }

    @Test
    void couleurValide_compile(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(color = "#FF0000")
            class Rouge {}

            @UMLClass(color = "LightBlue")
            class Bleu {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
    }

    // --- Les dépendances ---

    @Test
    void dependanceVersSoiMeme_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLDependency(target = Client.class)
            class Client {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("cannot depend on itself"), verdict.rapport());
    }

    @Test
    void dependanceEnDouble_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        // Le cas répétable : le compilateur emballe les deux dans @UMLDependencies, et le
        // processeur doit savoir déballer le conteneur pour les voir.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Service {}

            @UMLClass
            @UMLDependency(target = Service.class, label = "paie")
            @UMLDependency(target = Service.class, label = "paie")
            class Client {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("declared twice with the same label"), verdict.rapport());
    }

    @Test
    void dependancesRepeteesDistinctes_compilent(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Service {}

            @UMLClass
            class Journal {}

            @UMLClass
            @UMLDependency(target = Service.class, label = "paie")
            @UMLDependency(target = Journal.class, label = "trace")
            class Client {}
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
    }

    // --- Les contradictions ---

    @Test
    void includeEtExcludeEnsemble_arretentLaCompilation(@TempDir Path tmp) throws IOException {
        // La bibliothèque saurait départager — @UMLExclude l'emporte — mais appliquer une règle
        // en silence reviendrait à choisir à la place de l'auteur d'un code qui se contredit.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLInclude
                @UMLExclude
                private String nom;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("contradictory"), verdict.rapport());
    }

    // --- Les annotations sans effet ---

    @Test
    void associationSurUneClasseSansUmlClass_arreteLaCompilation(@TempDir Path tmp)
        throws IOException {
        // Le @UMLClass oublié : l'annotation est correcte en elle-même, mais ne produirait rien.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            class Client {
                @UMLAssociation(target = Commande.class)
                private Commande commande;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("drawn from nowhere"), verdict.rapport());
    }

    @Test
    void includeSurUneClasseSansUmlClass_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            class Client {
                @UMLInclude
                private String nom;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("has no effect"), verdict.rapport());
    }

    @Test
    void contrainteALaRegleVide_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLConstraint(rule = "  ")
            class Compte {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("cannot be empty"), verdict.rapport());
    }

    @Test
    void contrainteSurUneMethode_estRefuseeParJavacSeul(@TempDir Path tmp) throws IOException {
        // Une contrainte porte sur un état, pas sur un appel : @Target l'interdit, et c'est le
        // compilateur qui le dit.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Compte {
                @UMLConstraint(rule = "positif")
                public void crediter() {}
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
    }

    @Test
    void noteAuTexteVide_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLNote(text = "   ")
            class Client {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("cannot be empty"), verdict.rapport());
    }

    @Test
    void noteSurUneClasseSansUmlClass_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLNote(text = "Sans boite")
            class Client {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("nothing to hang on"), verdict.rapport());
    }

    @Test
    void deriveSurUneClasseSansUmlClass_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            class Personne {
                @UMLDerived
                private int age;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("has no effect"), verdict.rapport());
    }

    @Test
    void deriveSurUneClasse_estRefuseeParJavacSeul(@TempDir Path tmp) throws IOException {
        // La notion de classe dérivée n'existe pas en UML : @Target l'interdit, et c'est le
        // compilateur qui le dit, sans que le processeur ait à s'en mêler.
        Verdict verdict = compiler(tmp, """
            @UMLDerived
            @UMLClass
            class Personne {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
    }

    @Test
    void dependanceSurUneClasseSansUmlClass_arreteLaCompilation(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Service {}

            @UMLDependency(target = Service.class)
            class Client {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("drawn from nowhere"), verdict.rapport());
    }

    // --- Les textes vides ---

    @Test
    void nomFaitDEspaces_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(name = "   ")
            class Client {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("whitespace only"), verdict.rapport());
    }

    @Test
    void roleFaitDEspaces_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, targetRole = "  ")
                private Commande commande;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("whitespace only"), verdict.rapport());
    }

    // --- Ce que javac refusait déjà seul, et qui doit le rester ---

    @Test
    void associationSansCible_estRefuseeParJavacSeul(@TempDir Path tmp) throws IOException {
        // target() n'a pas de valeur par défaut : c'est le compilateur lui-même qui refuse, sans
        // le processeur. Ce test verrouille ce choix de conception, qu'un « default Object.class »
        // ajouté par mégarde ferait disparaître.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLAssociation(targetCardinality = "1")
                private String nom;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("target"), verdict.rapport());
    }
}
