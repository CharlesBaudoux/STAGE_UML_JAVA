package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationCobaye.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgRenommageTest {

    private static Verdict compiler(Path tmp, String code) throws IOException {
        return CompilationCobaye.compilerCorps(tmp, code);
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
        Path regles = tmp.resolve("umlg.json");
        java.nio.file.Files.writeString(regles, "{ \"strategy\": \"public\" }\n");

        Verdict verdict = CompilationCobaye.compilerCorps(tmp, """
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
