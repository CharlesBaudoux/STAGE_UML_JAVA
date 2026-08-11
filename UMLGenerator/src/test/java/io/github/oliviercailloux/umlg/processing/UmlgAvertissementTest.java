package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Ce que le processeur laisse compiler, mais pas en silence.
//
// La frontière avec UmlgProcessorTest est celle de la gravité, et elle a une règle : une erreur
// dit « ce code ne décrit aucun diagramme », un avertissement dit « ce code décrit un diagramme,
// mais vraisemblablement pas celui que tu crois ». D'où l'assertion commune à tous ces tests :
// la compilation réussit. Un avertissement qui arrêterait javac serait une erreur mal nommée.
class UmlgAvertissementTest {

    private static final String ENTETE = """
        import io.github.oliviercailloux.umlg.annotation.*;
        import java.util.List;
        import java.util.Map;
        """;

    private static Verdict compiler(Path tmp, String corps) throws IOException {
        return CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + corps);
    }

    // Vérifie la gravité en même temps que le message : c'est la moitié de ce que chaque test dit.
    private static void assertAvertit(Verdict verdict, String fragment) {
        assertTrue(verdict.reussie(), "Un avertissement ne doit pas arrêter javac : "
            + verdict.rapport());
        assertTrue(verdict.avertissementContient(fragment), verdict.rapport());
    }

    // --- Les dérogations qui ne dérogent à rien ---

    @Test
    void includeSurUnAttributDejaVisible_seTaitSansLesRegles(@TempDir Path tmp) throws IOException {
        // Le reproche « @UMLInclude ne sert à rien, la stratégie montre déjà ce champ » dépend de
        // deux axes, pas d'un : la stratégie, que la classe déclare ici en toutes lettres, et les
        // motifs « hide » du fichier de règles, que javac ne lit pas sans -Aumlg.rules. Sous
        // « hide nom », ce @UMLInclude serait le seul moyen de garder la ligne, et le retirer
        // comme le processeur le conseillait effaçait le champ du diagramme.
        //
        // Le même code, l'option donnée, retrouve son avertissement : voir UmlgOptionReglesTest.
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLInclude
                public String nom;
            }
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
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
        // Le cas même de la section « Dérogation d'inclusion » : un privé rendu visible sous une
        // stratégie qui l'aurait masqué. Rien à signaler ici, sous peine de rendre les
        // avertissements inaudibles.
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
        // Une méthode publique peut être écartée par le filtre anti-bruit — un getter —, auquel
        // cas @UMLInclude sert. Le processeur n'a pas ce filtre sous la main : il se tait plutôt
        // que de reprocher à tort.
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLInclude
                public String getNom() { return ""; }
            }
            """);

        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    // --- Les dérogations posées sur une association ---

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

    // --- L'association écrite dans la boîte ---

    @Test
    void parametresDeTraitSousLaNotationAttribute_avertissent(@TempDir Path tmp)
        throws IOException {
        // Une ligne d'attribut n'a qu'un type et une multiplicité : le losange, le rôle et le
        // libellé n'ont nulle part où s'écrire, et disparaîtraient sans un mot.
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
        // Les paramètres fautifs sont nommés, et eux seuls : targetCardinality, qui s'écrit bel
        // et bien, ne doit pas figurer dans la liste.
        assertAvertit(verdict, "type, label");
    }

    @Test
    void parametreDeTraitFautifSousLaNotationAttribute_estRefuseEtSignale(@TempDir Path tmp)
        throws IOException {
        // Le processeur disait deux choses contraires du même paramètre : « 3..1 » y était refusé
        // comme partout, et la ligne suivante annonçait que sourceCardinality ne serait de toute
        // façon pas dessinée. L'auteur corrigeait une valeur pour s'entendre dire ensuite de
        // l'effacer.
        //
        // Il dit maintenant les deux ensemble, et dans cet ordre : une valeur impossible reste
        // impossible — la génération la refuse aussi —, et elle est de surcroît inutile ici.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Commande {}

            @UMLClass
            class Client {
                @UMLAssociation(target = Commande.class, notation = UMLNotation.ATTRIBUTE,
                    sourceCardinality = "3..1")
                private Commande commande;
            }
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("the lower bound exceeds the upper bound"),
            verdict.rapport());
        assertTrue(verdict.avertissementContient("decorate a path between two boxes"),
            "L'inutilité du paramètre doit être dite en même temps que sa faute : "
                + verdict.rapport());
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
        // Le message de la notation ARROW — « @UMLExclude retire le trait » — serait faux ici :
        // il n'y a pas de trait, c'est la ligne de la boîte qui s'en va.
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
    void includeSurUneAssociationDansLaBoite_seTaitSansLesRegles(@TempDir Path tmp)
        throws IOException {
        // « Déjà montré, quelle que soit la stratégie » était vrai de la stratégie et faux du
        // fichier de règles : le filtre anti-bruit écarte aussi cette ligne-là. Sans le fichier,
        // le processeur ne peut pas savoir, et se tait.
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

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void deriveSurUneAssociationDansLaBoite_neDitRien(@TempDir Path tmp) throws IOException {
        // Sous la notation ARROW, @UMLDerived sans targetRole n'a rien à marquer et le
        // processeur le signale. Ici la barre oblique se pose devant le nom de l'attribut, qui
        // est écrit : il n'y a rien à reprocher.
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
        // L'association ne tire aucun trait : la dépendance est le seul lien du dessin, elle
        // n'encombre rien.
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

    // --- La multiplicité qui ne se verra pas ---

    @Test
    void multipliciteSurUnMembreMasque_avertit(@TempDir Path tmp) throws IOException {
        // Des crochets ne se voient que si le membre qu'ils suivent est dessiné.
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

    // --- Le stéréotype ---

    @Test
    void stereotypeDejaEncadre_avertit(@TempDir Path tmp) throws IOException {
        // La faute est invisible autrement : rien n'échoue, la boîte affiche seulement quatre
        // chevrons de chaque côté.
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

    // --- Deux boîtes indiscernables ---

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

    // --- La dépendance qui double une association ---

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

    // --- La multiplicité confrontée au type ---

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

    // --- La cible confrontée au type ---

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
        // Map<String, List<Commande>> : la cible est à deux niveaux de profondeur, et la
        // recherche doit descendre jusque-là plutôt que de conclure trop vite.
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
        // Une « List » nue ne dit rien de ce qu'elle contient : c'est le manque exact que
        // target() vient combler, et le lui reprocher reviendrait à condamner le remède.
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

    // --- Les éléments dérivés ---

    @Test
    void deriveSurUnMembreNonAffiche_avertit(@TempDir Path tmp) throws IOException {
        // La barre oblique ne se voit que si le membre se voit.
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
    void deriveSurUnMembreExclu_avertit(@TempDir Path tmp) throws IOException {
        // Le pendant de deriveRattrapeParUnInclude : la question « ce membre sera-t-il dessiné ? »
        // consulte les deux dérogations, et @UMLExclude retire un membre que la stratégie
        // montrerait. La barre oblique s'en va avec lui.
        Verdict verdict = compiler(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Personne {
                @UMLDerived
                @UMLExclude
                public int age;
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
        // @UMLInclude rend le membre visible : la dérivation retrouve un porteur, et le contrôle
        // doit en tenir compte plutôt que de ne regarder que la stratégie.
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
        // Sur une association, la barre se pose devant le rôle cible. Sans rôle, la propriété
        // n'est nommée nulle part et rien ne peut la porter.
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

    // --- Les notes ---

    @Test
    void noteSurUnMembreNonAffiche_avertit(@TempDir Path tmp) throws IOException {
        // La bulle serait dessinée dans le vide : l'extraction l'écarte, et c'est ici qu'il faut
        // le dire.
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
        // PlantUML ne place une note de membre qu'à gauche ou à droite. Le rendu se rabat sur la
        // droite plutôt que d'écrire une syntaxe qui ferait rejeter le document entier ; reste à
        // dire à l'auteur que son orientation a été perdue, là où il l'a écrite.
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
        // Les quatre côtés existent bel et bien pour une classe : l'avertissement ne doit pas
        // déborder sur elle.
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

    // --- Les contraintes ---

    @Test
    void regleDejaEntreAccolades_avertit(@TempDir Path tmp) throws IOException {
        // Même méprise que les guillemets du stéréotype : la notation d'UML est si liée aux
        // accolades qu'on les écrit sans y penser.
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
        // L'attribut n'est pas dessiné, mais le lien l'est : la contrainte part avec lui. C'est
        // la règle d'arbitrage de la spécification, et le cas majoritaire.
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

    // --- Le silence sur demande ---

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
        // Une erreur dit qu'aucun diagramme ne peut être produit : personne ne peut vouloir en
        // être dispensé, et l'option ne doit pas offrir de le faire par mégarde.
        Verdict verdict = CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + """
            @UMLClass(color = "PasUneCouleur")
            class Client {}
            """, List.of("-Aumlg.warnings=none"));

        assertTrue(!verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("Color unknown to PlantUML"), verdict.rapport());
    }

    @Test
    void optionAvecUneValeurInconnue_estRefusee(@TempDir Path tmp) throws IOException {
        Verdict verdict = CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + """
            @UMLClass
            class Client {}
            """, List.of("-Aumlg.warnings=off"));

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("unknown value"), verdict.rapport());
    }

    @Test
    void optionSansValeur_estRefusee(@TempDir Path tmp) throws IOException {
        // « -Aumlg.warnings » tout court est la faute de frappe même que le contrôle des valeurs
        // dit avoir corrigée, et elle passait par le même trou : javac range alors la clé avec
        // une valeur nulle, que le test « l'option est-elle absente ? » confondait avec l'absence.
        // L'utilisateur croyait avoir fait taire le processeur, et se demandait pourquoi il
        // parlait encore.
        Verdict verdict = CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + """
            @UMLClass(stereotype = "<<Service>>")
            class Paiement {}
            """, List.of("-Aumlg.warnings"));

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("no value"), verdict.rapport());
        // Le message dit quoi écrire, et non seulement ce qui ne va pas.
        assertTrue(verdict.erreurContient("-Aumlg.warnings=none"), verdict.rapport());
    }
}
