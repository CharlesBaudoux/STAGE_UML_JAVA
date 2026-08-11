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

    // L'en-tête que partagent tous les cobayes vit dans CompilationCobaye, avec le harnais.
    private static Verdict compiler(Path tmp, String corps) throws IOException {
        return CompilationCobaye.compilerCorps(tmp, corps);
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

    // --- La cible déduite du type de l'attribut : le processeur doit dire ce que dit la
    // génération, ni plus ni moins ---

    @Test
    void associationSansCible_versUnTypeHorsDiagramme_arreteLaCompilation(@TempDir Path tmp)
        throws IOException {
        // Ce test verrouillait un choix de conception qui n'existe plus : target() n'avait pas de
        // valeur par défaut, et c'était javac seul qui refusait l'annotation qui l'omettait. Le
        // paramètre porte aujourd'hui « default void.class » — la sentinelle « rien d'écrit », qui
        // déclenche la déduction depuis le type de l'attribut. Le test continuait de passer, mais
        // sur un tout autre motif que celui qu'il annonçait.
        //
        // Ce qu'il garde désormais : la déduction ne relâche pas le contrôle. « private String
        // nom » donne bien une cible — String —, et cette cible est refusée comme n'importe quelle
        // autre classe hors du diagramme. La sentinelle ouvre la porte au type de l'attribut, pas
        // au premier type venu.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLAssociation(targetCardinality = "1")
                private String nom;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("target java.lang.String does not carry @UMLClass"),
            verdict.rapport());
        // Et le remède nomme le paramètre à écrire, plutôt que de constater seulement.
        assertTrue(verdict.erreurContient("point target() at a class that is"), verdict.rapport());
    }

    @Test
    void associationSurUnChampDeTypeVariable_compile(@TempDir Path tmp) throws IOException {
        // Le processeur arrêtait la compilation d'un code que la génération dessinait sans
        // broncher : il tenait « T » pour indéterminé, là où la réflexion lit l'effacement,
        // c'est-à-dire la borne. C'est l'écart le plus coûteux des deux moitiés, puisqu'il bloque
        // l'utilisateur au lieu de le laisser voir un dessin discutable.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Boite<T extends Commande> {
                @UMLAssociation(targetCardinality = "1")
                private T contenu;
            }
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void collectionBruteSansCible_nommeLeTypeFautif(@TempDir Path tmp) throws IOException {
        // Le message vient désormais de CibleDeduite, à qui la règle appartient : celui du
        // processeur ne nommait pas le type, et un projet à plusieurs associations laissait son
        // auteur chercher laquelle était visée.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLAssociation(targetCardinality = "0..*")
                private List commandes;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("the field's type (List) does not say what it contains"),
            verdict.rapport());
    }

    @Test
    void champPrimitifSansCible_nommeLeTypeFautif(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLAssociation(targetCardinality = "1")
                private int compteur;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(
            verdict.erreurContient("the field's type (int) does not name a class of the diagram"),
            verdict.rapport());
    }

    @Test
    void contrainteVide_reprendLeMessageDuModele(@TempDir Path tmp) throws IOException {
        // La règle n'est plus réécrite ici : le refus de javac est mot pour mot celui que la
        // génération aurait produit. Les deux textes avaient déjà divergé.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLConstraint(rule = " ")
            class Compte {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient(
            "A constraint cannot be empty or made of whitespace only."), verdict.rapport());
    }
}
