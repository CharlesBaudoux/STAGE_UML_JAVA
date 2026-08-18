package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgRulesOptionTest {

    private static final String Fixture = """
        @UMLClass
        class Client {
            @UMLInclude
            public String name;
        }
        """;

    private static Path writeRules(Path tmp, String content) throws IOException {
        Path file = tmp.resolve("umlg.json");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static Verdict compiler(Path tmp, List<String> options) throws IOException {
        return CompilationFixture.compileBody(tmp, Fixture, options);
    }

    @Test
    void withoutTheOption_theProcessorStaysSilent(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, List.of());

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void withTheOption_theWarningComesBack(@TempDir Path tmp) throws IOException {
        Path rules = writeRules(tmp, "{ \"strategy\": \"public\" }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + rules));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warningContains("@UMLInclude has no effect"), verdict.report());
        assertTrue(verdict.warningContains("PUBLIC"),
            "The message must name the strategy actually applied: " + verdict.report());
    }

    @Test
    void withAProjectShowingNothing_nothingIsReported(@TempDir Path tmp) throws IOException {
        Path rules = writeRules(tmp, "{ \"strategy\": \"none\" }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + rules));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void aPatternHidingTheField_doesNotComplainAboutTheInclude(@TempDir Path tmp) throws IOException {
        Path rules = writeRules(tmp, "{ \"strategy\": \"public\", \"hide\": [\"name\"] }");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + rules));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void onAnInBoxAssociation_theWarningAlsoDependsOnTheRules(@TempDir Path tmp)
        throws IOException {
        String body = """
            @UMLClass
            class Order {}

            @UMLClass
            class Client {
                @UMLInclude
                @UMLAssociation(target = Order.class, notation = UMLNotation.ATTRIBUTE)
                private Order order;
            }
            """;
        Path rules = writeRules(tmp, "{ \"strategy\": \"public\" }");

        Verdict withRules = CompilationFixture.compileBody(tmp, body,
            List.of("-Aumlg.rules=" + rules));

        assertTrue(withRules.successful(), withRules.report());
        assertTrue(withRules.warningContains("already shown in the attribute compartment"),
            withRules.report());

        Path hiding = writeRules(tmp, "{ \"strategy\": \"public\", \"hide\": [\"order\"] }");

        Verdict hidden = CompilationFixture.compileBody(tmp, body,
            List.of("-Aumlg.rules=" + hiding));

        assertTrue(hidden.successful(), hidden.report());
        assertTrue(hidden.warnings().isEmpty(), hidden.report());
    }

    @Test
    void theEffectlessExclude_speaksWithoutTheRules(@TempDir Path tmp) throws IOException {
        Verdict verdict = CompilationFixture.compileBody(tmp, """
            @UMLClass(strategy = UMLVisibility.PUBLIC)
            class Client {
                @UMLExclude
                private String passedWord;
            }
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warningContains("@UMLExclude has no effect"), verdict.report());
    }

    @Test
    void missingFile_isReportedWithoutStoppingCompilation(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + tmp.resolve("absent.json")));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warningContains("no such file"), verdict.report());
    }

    @Test
    void missingRelativePath_saysWhereItLooked(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=rules/umlg.json"));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warningContains("looked for "), verdict.report());
        assertTrue(verdict.warningContains(
            Path.of("rules/umlg.json").toAbsolutePath().toString()), verdict.report());
        assertTrue(verdict.warningContains("working directory"), verdict.report());
    }

    @Test
    void nowarn_doesNotSilenceTheConfigurationWarning(@TempDir Path tmp) throws IOException {
        Verdict verdict = CompilationFixture.compileBody(tmp, """
            @UMLClass(stereotype = "<<Service>>")
            class Payment {}
            """, List.of("-nowarn", "-Aumlg.rules=" + tmp.resolve("absent.json")));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warningContains("no such file"), verdict.report());
        assertTrue(verdict.warnings().stream()
            .noneMatch(message -> message.contains("already contains the quotes")),
            "-nowarn must silence what the processor found in the code: " + verdict.report());
    }

    @Test
    void faultyFile_stopsCompilation(@TempDir Path tmp) throws IOException {
        Path rules = writeRules(tmp, "{ \"color\": { \"Client\": \"red\" } }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + rules));

        assertFalse(verdict.successful(), "A faulty rules file describes no diagram.");
        assertTrue(verdict.errorContains("unknown rule"), verdict.report());
    }

    @Test
    void silencedWarnings_alsoSilenceThoseTheOptionAddsBack(@TempDir Path tmp)
        throws IOException {
        Path rules = writeRules(tmp, "{ \"strategy\": \"public\" }\n");

        Verdict verdict =
            compiler(tmp, List.of("-Aumlg.rules=" + rules, "-Aumlg.warnings=none"));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }
}
