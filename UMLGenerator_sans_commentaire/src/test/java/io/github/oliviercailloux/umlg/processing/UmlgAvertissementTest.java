package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgAvertissementTest {

    private static final String ENTETE = """
        import io.github.oliviercailloux.umlg.annotation.*;
        import java.util.List;
        import java.util.Map;
        """;

    private static Verdict compiler(Path tmp, String corps) throws IOException {
        return CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + corps);
    }

    private static void assertAvertit(Verdict verdict, String fragment) {
        assertTrue(verdict.reussie(), "Un avertissement ne doit pas arrêter javac : "
            + verdict.rapport());
        assertTrue(verdict.avertissementContient(fragment), verdict.rapport());
    }

    @Test
    void includeSurUnAttributDejaVisible_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLInclude
                public String nom;
            }
            """);

        assertAvertit(verdict, "@UMLInclude has no effect");
        assertAvertit(verdict, "strategy = PUBLIC");
    }

    @Test
    void excludeSurUnMembreDejaMasque_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLExclude
                private String motDePasse;
            }
            """);

        assertAvertit(verdict, "@UMLExclude has no effect");
    }

    @Test
    void derogationUtile_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class MoteurDeRecherche {
                @UMLInclude
                private int compteur;

                @UMLExclude
                public String trace;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void includeSurUneMethodePublique_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLInclude
                public String getNom() { return ""; }
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void excludeSurUneAssociation_avertitQueLeLienDisparait(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Client {
                @UMLExclude
                @UMLAssociation(target = Commande.class)
                private Commande commande;
            }
            """);

        assertAvertit(verdict, "removes the link between the two boxes");
    }

    @Test
    void includeSurUneAssociation_avertitQueLAttributNeRevientPas(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLInclude
                @UMLAssociation(target = Commande.class)
                private Commande commande;
            }
            """);

        assertAvertit(verdict, "never inside one");
    }

    @Test
    void parametresDeTraitSousLaNotationAttribute_avertissent(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*",
                    notation = UMLNotation.ATTRIBUTE, type = UMLRelationType.COMPOSITION,
                    label = "passe")
                private List<Commande> commandes;
            }
            """);

        assertAvertit(verdict, "decorate a path between two boxes");
        assertAvertit(verdict, "type, label");
    }

    @Test
    void notationAttributeSansParametreDeTrait_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*",
                    notation = UMLNotation.ATTRIBUTE)
                private List<Commande> commandes;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void excludeSurUneAssociationDansLaBoite_avertitQueLaLigneDisparait(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Client {
                @UMLExclude
                @UMLAssociation(target = Commande.class, notation = UMLNotation.ATTRIBUTE)
                private Commande commande;
            }
            """);

        assertAvertit(verdict, "removes the very line that association asked for");
    }

    @Test
    void includeSurUneAssociationDansLaBoite_avertitQueLaLigneYEstDeja(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLInclude
                @UMLAssociation(target = Commande.class, notation = UMLNotation.ATTRIBUTE)
                private Commande commande;
            }
            """);

        assertAvertit(verdict, "already shown in the attribute compartment");
    }

    @Test
    void deriveSurUneAssociationDansLaBoite_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLDerived
                @UMLAssociation(target = Commande.class, notation = UMLNotation.ATTRIBUTE)
                private Commande commande;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void dependanceDoublantUneAssociationDansLaBoite_neDitRien(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            @UMLDependency(target = Commande.class, label = "facture")
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*",
                    notation = UMLNotation.ATTRIBUTE)
                private List<Commande> commandes;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void multipliciteSurUnMembreMasque_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Mnemonique {
                @UMLMultiplicity("0..*")
                private String alias;
            }
            """);

        assertAvertit(verdict, "@UMLMultiplicity has no visible effect");
    }

    @Test
    void multipliciteVide_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Mnemonique {
                @UMLMultiplicity("")
                private String alias;
            }
            """);

        assertAvertit(verdict, "asks for nothing");
    }

    @Test
    void multipliciteVisible_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Mnemonique {
                @UMLMultiplicity("0..*")
                private String alias;

                @UMLMultiplicity("0..1")
                public String ucp() { return ""; }
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void stereotypeDejaEncadre_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(stereotype = "<<Service>>")
            class Paiement {}
            """);

        assertAvertit(verdict, "already contains the guillemets");
    }

    @Test
    void stereotypeSimple_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(stereotype = "Service")
            class Paiement {}
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void deuxClassesDeMemeNomAffiche_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {}

            @UMLClass(name = "Client")
            class Acheteur {}
            """);

        assertAvertit(verdict, "the reader cannot tell apart");
    }

    @Test
    void dependanceDoublantUneAssociation_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            @UMLDependency(target = Commande.class, label = "facture")
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*")
                private List<Commande> commandes;
            }
            """);

        assertAvertit(verdict, "already links it to");
    }

    @Test
    void dependanceVersUneAutreClasse_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Journal {}

            @UMLClass
            @UMLDependency(target = Journal.class, label = "trace")
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*")
                private List<Commande> commandes;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void collectionSansMultiplicite_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class)
                private List<Commande> commandes;
            }
            """);

        assertAvertit(verdict, "targetCardinality is not set");
    }

    @Test
    void collectionAvecMultipliciteUnitaire_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "0..1")
                private List<Commande> commandes;
            }
            """);

        assertAvertit(verdict, "allows at most one object");
    }

    @Test
    void attributSimpleAvecMultipliciteMultiple_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "1..*")
                private Commande commande;
            }
            """);

        assertAvertit(verdict, "holds a single one");
    }

    @Test
    void tableauCompteCommeCollection(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "1")
                private Commande[] commandes;
            }
            """);

        assertAvertit(verdict, "allows at most one object");
    }

    @Test
    void multipliciteAccordeeAuType_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*")
                private List<Commande> commandes;

                @UMLAssociation(target = Commande.class, targetCardinality = "0..1")
                private Commande derniere;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void cibleAbsenteDuTypeDeLAttribut_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Facture {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "0..1")
                private Facture facture;
            }
            """);

        assertAvertit(verdict, "does not appear anywhere in the field's type");
    }

    @Test
    void cibleTrouveeAuFondDunGenerique_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*")
                private Map<String, List<Commande>> parReference;

                @UMLAssociation(target = Commande.class, targetCardinality = "0..*")
                private List<? extends Commande> variantes;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void collectionBrute_neReprochePasLaCible(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*")
                private List commandes;
            }
            """);

        assertTrue(verdict.avertissements().stream()
            .noneMatch(message -> message.contains("does not appear anywhere")), verdict.rapport());
    }

    @Test
    void deriveSurUnMembreNonAffiche_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Personne {
                @UMLDerived
                private int age;
            }
            """);

        assertAvertit(verdict, "@UMLDerived has no visible effect");
    }

    @Test
    void deriveSurUnMembreAffiche_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Personne {
                @UMLDerived
                private int age;

                @UMLDerived
                public String resumer() { return ""; }
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void deriveRattrapeParUnInclude_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Personne {
                @UMLDerived
                @UMLInclude
                private int age;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void lienDeriveSansRoleCible_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLDerived
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*")
                private List<Commande> commandes;
            }
            """);

        assertAvertit(verdict, "targetRole is not set");
    }

    @Test
    void lienDeriveAvecRoleCible_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLDerived
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*",
                    targetRole = "encours")
                private List<Commande> commandes;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void noteSurUnMembreNonAffiche_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Compte {
                @UMLNote(text = "Jamais negatif")
                private double solde;
            }
            """);

        assertAvertit(verdict, "nothing to hang on");
    }

    @Test
    void noteSurUnAttributPromuEnLien_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass(strategy = UMLVisibility.PRIVATE)
            class Client {
                @UMLNote(text = "Perdue")
                @UMLAssociation(target = Commande.class, targetCardinality = "0..1")
                private Commande commande;
            }
            """);

        assertAvertit(verdict, "a note cannot hang on a link");
    }

    @Test
    void directionVerticaleSurUneNoteDeMembre_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Compte {
                @UMLNote(text = "Jamais negatif", direction = UMLDirection.DOWN)
                public double solde;
            }
            """);

        assertAvertit(verdict, "direction = DOWN has no effect");
        assertAvertit(verdict, "drawn on the right");
    }

    @Test
    void directionVerticaleSurUneNoteDeClasse_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            @UMLNote(text = "Coeur du domaine", direction = UMLDirection.UP)
            class Compte {
                public double solde;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void notesBienPlacees_neDisentRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            @UMLNote(text = "Coeur du domaine")
            @UMLNote(text = "Relire le contrat", direction = UMLDirection.LEFT)
            class Compte {
                @UMLNote(text = "Jamais negatif")
                public double solde;

                @UMLNote(text = "Algorithme critique", direction = UMLDirection.LEFT)
                public void verifier() {}
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void regleDejaEntreAccolades_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLConstraint(rule = "{titulaire majeur}")
            class Compte {}
            """);

        assertAvertit(verdict, "already carries the braces");
    }

    @Test
    void contrainteSurUnAttributNonAffiche_avertit(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Compte {
                @UMLConstraint(rule = "positif")
                private double solde;
            }
            """);

        assertAvertit(verdict, "will not be written anywhere");
    }

    @Test
    void contrainteSurUnAttributPromuEnLien_neDitRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLConstraint(rule = "subset")
                @UMLAssociation(target = Commande.class, targetCardinality = "0..*",
                    targetRole = "achats")
                private List<Commande> commandes;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void contraintesBienPlacees_neDisentRien(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            @UMLConstraint(rule = "titulaire majeur")
            class Compte {
                @UMLConstraint(rule = "solde >= 0")
                public double solde;
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void optionNone_faitTaireLesAvertissements(@TempDir Path tmp) throws IOException {
        String corps = """
            @UMLClass(stereotype = "<<Service>>")
            class Paiement {}
            """;

        Verdict bavard = compiler(tmp, corps);
        assertTrue(!bavard.avertissements().isEmpty(), "Le témoin doit bien avertir.");

        Verdict muet = CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + corps,
            List.of("-Aumlg.warnings=none"));

        assertTrue(muet.reussie(), muet.rapport());
        assertTrue(muet.avertissements().isEmpty(), muet.rapport());
    }

    @Test
    void optionNone_neFaitPasTaireLesErreurs(@TempDir Path tmp) throws IOException {
        Verdict verdict = CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + """
            @UMLClass(color = "PasUneCouleur")
            class Client {}
            """, List.of("-Aumlg.warnings=none"));

        assertTrue(!verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("Color unknown to PlantUML"), verdict.rapport());
    }
}
