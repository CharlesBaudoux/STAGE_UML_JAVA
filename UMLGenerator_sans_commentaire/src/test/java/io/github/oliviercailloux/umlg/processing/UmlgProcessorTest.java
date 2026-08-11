package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgProcessorTest {

    private static final String ENTETE = """
        import io.github.oliviercailloux.umlg.annotation.*;
        import java.util.List;
        """;

    private static Verdict compiler(Path tmp, String corps) throws IOException {
        return CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + corps);
    }

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
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

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
        assertTrue(verdict.erreurContient("Annotate it"), verdict.rapport());
    }

    @Test
    void ciblePrimitive_arreteLaCompilation(@TempDir Path tmp) throws IOException {
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
        assertTrue(verdict.erreurContient("sourceCardinality"), verdict.rapport());
    }

    @Test
    void couleurInconnueDePlantUml_arreteLaCompilation(@TempDir Path tmp) throws IOException {
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

    @Test
    void includeEtExcludeEnsemble_arretentLaCompilation(@TempDir Path tmp) throws IOException {
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

    @Test
    void associationSurUneClasseSansUmlClass_arreteLaCompilation(@TempDir Path tmp)
        throws IOException {
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

    @Test
    void associationSansCible_estRefuseeParJavacSeul(@TempDir Path tmp) throws IOException {
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
