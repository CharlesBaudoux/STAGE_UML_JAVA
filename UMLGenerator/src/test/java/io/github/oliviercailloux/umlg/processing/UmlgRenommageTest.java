package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Le renommage d'un membre — @UMLInclude(name = ...) — vu du processeur.
//
// Trois écarts, trois réponses. Un nom fait d'espaces ne décrit aucun diagramme : erreur. Deux
// noms pour la même ligne — targetRole et name — se contredisent : erreur. Un nom identique à
// celui du code est licite mais ne change rien : avertissement, pour que l'auteur n'aille pas
// chercher sur l'image un renommage qui n'a pas eu lieu.
class UmlgRenommageTest {

    private static final String ENTETE = "import io.github.oliviercailloux.umlg.annotation.*;\n";

    private static Verdict compiler(Path tmp, String code) throws IOException {
        return CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + code);
    }

    @Test
    void unRenommageOrdinaire_compileSansUnMot(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLInclude(name = "xKeycodeByXKeyName")
                public String codeByName;
            }
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }

    @Test
    void unNomFaitDEspaces_arreteLaCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLInclude(name = "   ")
                public String nom;
            }
            """);

        assertFalse(verdict.reussie(), "Une ligne au nom invisible ne décrit aucun diagramme.");
        assertTrue(verdict.erreurContient("whitespace"), verdict.rapport());
    }

    @Test
    void targetRoleEtName_ensembleSontRefuses(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLInclude(name = "unNom")
                @UMLAssociation(target = Adresse.class, targetRole = "unAutre",
                    notation = UMLNotation.ATTRIBUTE)
                public Adresse adresse;
            }

            @UMLClass
            class Adresse {}
            """);

        assertFalse(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.erreurContient("two names"), verdict.rapport());
    }

    @Test
    void renommerVersSonPropreNom_estSignaleSansArreter(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLInclude(name = "nom")
                public String nom;
            }
            """);

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissementContient("name it already has"), verdict.rapport());
    }

    @Test
    void unRenommage_faitTaireLAvertissementSansEffet(@TempDir Path tmp) throws IOException {
        // Sous « strategy public », un @UMLInclude nu ne déroge à rien et serait signalé. Le
        // même @UMLInclude portant un nom fait quelque chose — il renomme — et le signalement
        // deviendrait un contresens.
        Path regles = tmp.resolve("umlg.json");
        java.nio.file.Files.writeString(regles, "{ \"strategy\": \"public\" }\n");

        Verdict verdict = CompilationCobaye.compiler(tmp, "Cobaye.java", ENTETE + """
            @UMLClass
            class Client {
                @UMLInclude(name = "identite")
                public String nom;
            }
            """, java.util.List.of("-Aumlg.rules=" + regles));

        assertTrue(verdict.reussie(), verdict.rapport());
        assertTrue(verdict.avertissements().isEmpty(), verdict.rapport());
    }
}
