package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The renaming of a member — @UMLInclude(name = ...) — seen from the processor.
//
// Three deviations, three answers. A name made of spaces describes no diagram: an error. Two
// names for the same line — targetRole and name — contradict each other: an error. A name identical to
// the code order is valid but changes nothing: a warning, so that the author does not go
// look on the image for a renaming that did not take place.
class UmlgRenamingTest {

    // The header every fixture shares lives in CompilationFixture, with the harness.
    private static Verdict compiler(Path tmp, String code) throws IOException {
        return CompilationFixture.compileBody(tmp, code);
    }

    @Test
    void anOrdinaryRenaming_compilesWithoutAWord(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLInclude(name = "xKeycodeByXKeyName")
                public String codeByName;
            }
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void aNameMadeOfSpaces_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLInclude(name = "   ")
                public String name;
            }
            """);

        assertFalse(verdict.successful(), "A line with an invisible name describes no diagram.");
        assertTrue(verdict.errorContains("whitespace"), verdict.report());
    }

    @Test
    void targetRoleAndName_areRefusedTogether(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLInclude(name = "oneName")
                @UMLAssociation(target = Address.class, targetRole = "another",
                    notation = UMLNotation.ATTRIBUTE)
                public Address address;
            }

            @UMLClass
            class Address {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("two names"), verdict.report());
    }

    @Test
    void renamingToItsOwnName_isReportedWithoutStopping(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Client {
                @UMLInclude(name = "name")
                public String name;
            }
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warningContains("name it already has"), verdict.report());
    }

    @Test
    void aRenaming_silencesTheEffectlessWarning(@TempDir Path tmp) throws IOException {
        // Under "strategy public", a bare @UMLInclude exempts nothing and would be reported. The
        // the same @UMLInclude carrying a name does something — it renames — and the report
        // deviendrait un contresens.
        Path rules = tmp.resolve("umlg.json");
        java.nio.file.Files.writeString(rules, "{ \"strategy\": \"public\" }\n");

        Verdict verdict = CompilationFixture.compileBody(tmp, """
            @UMLClass
            class Client {
                @UMLInclude(name = "identity")
                public String name;
            }
            """, java.util.List.of("-Aumlg.rules=" + rules));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }
}
