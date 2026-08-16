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

// The -Aumlg.rules option: what the processor finds again when given the rules file.
//
// WHAT THESE TESTS PROTECT
//
// The default of @UMLClass is PROJECT, that is, "the strategy umlg.json declares". This
// file lives at the root of the compiled classes, and javac does not know where it is: without
// the option, the processor cannot say whether a member will be drawn, and stays silent on everything that
// depends on it. The option gives it back the view, and it is the only thing the library silences.
//
// Each test therefore compares both worlds: without the option, silence; with it, the same warning
// than if the class had declared its strategy in full.
class UmlgRulesOptionTest {

    // A class deferring to the project, and an @UMLInclude on a public member. Useful or not
    // depending on what the file declares: that is the whole matter at hand.
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
        // The default behaviour: undecidable, hence silent. Nothing is wrong for all that.
        Verdict verdict = compiler(tmp, List.of());

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void withTheOption_theWarningComesBack(@TempDir Path tmp) throws IOException {
        // "\"strategy\": \"public\"" already displays the field: the @UMLInclude exempts nothing, and the
        // the processor can at last say it.
        Path rules = writeRules(tmp, "{ \"strategy\": \"public\" }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + rules));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warningContains("@UMLInclude has no effect"), verdict.report());
        assertTrue(verdict.warningContains("PUBLIC"),
            "The message must name the strategy actually applied: " + verdict.report());
    }

    @Test
    void withAProjectShowingNothing_nothingIsReported(@TempDir Path tmp) throws IOException {
        // The other half, which counts as much: @UMLInclude is indispensable here, and the
        // the processor must stay silent. A warning would be a contradiction.
        Path rules = writeRules(tmp, "{ \"strategy\": \"none\" }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + rules));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void aPatternHidingTheField_doesNotComplainAboutTheInclude(@TempDir Path tmp) throws IOException {
        // The heart of the matter: "strategy public" would display this field, but "hide name"
        // drops it, and @UMLInclude is then the only way of keeping it. Following it — "remove
        // it" — erased the line from the diagram.
        Path rules = writeRules(tmp, "{ \"strategy\": \"public\", \"hide\": [\"name\"] }");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + rules));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    @Test
    void onAnInBoxAssociation_theWarningAlsoDependsOnTheRules(@TempDir Path tmp)
        throws IOException {
        // "Already shown in the field compartment, whatever the strategy": true
        // of the strategy, false of the noise filter, which drops that line too. With the
        // the rules, and them alone, the processor can conclude.
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

        // And the pattern hiding that field shuts the processor up.
        Path hiding = writeRules(tmp, "{ \"strategy\": \"public\", \"hide\": [\"order\"] }");

        Verdict hidden = CompilationFixture.compileBody(tmp, body,
            List.of("-Aumlg.rules=" + hiding));

        assertTrue(hidden.successful(), hidden.report());
        assertTrue(hidden.warnings().isEmpty(), hidden.report());
    }

    @Test
    void theEffectlessExclude_speaksWithoutTheRules(@TempDir Path tmp) throws IOException {
        // The warning in the reverse direction has no such need: a pattern can only hide
        // further, so that a member already hidden by the strategy certainly is. The
        // the tightening must therefore not spill over onto it.
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
        // The user named a file they did not write: their code stays right, but they
        // expects checks it will not get. A warning, then, and not an error.
        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + tmp.resolve("absent.json")));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warningContains("no such file"), verdict.report());
    }

    @Test
    void missingRelativePath_saysWhereItLooked(@TempDir Path tmp) throws IOException {
        // A relative path is resolved against javac's working directory, which is not
        // the project's own as soon as the build is started from elsewhere. The message merely
        // repeating what the user had written, which taught them nothing: they were looking for
        // a file that was nevertheless right there.
        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=rules/umlg.json"));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warningContains("looked for "), verdict.report());
        assertTrue(verdict.warningContains(
            Path.of("rules/umlg.json").toAbsolutePath().toString()), verdict.report());
        assertTrue(verdict.warningContains("working directory"), verdict.report());
    }

    @Test
    void nowarn_doesNotSilenceTheConfigurationWarning(@TempDir Path tmp) throws IOException {
        // Silencing the warnings means "do not tell me what you found in my
        // code"; that cannot mean "do not tell me you are misconfigured". The
        // working around -Aumlg.warnings=none was nevertheless useless, for lack of being written
        // at the severity -nowarn respects — and Maven passes -nowarn by default, so that
        // the ordinary case was the silent case.
        Verdict verdict = CompilationFixture.compileBody(tmp, """
            @UMLClass(stereotype = "<<Service>>")
            class Payment {}
            """, List.of("-nowarn", "-Aumlg.rules=" + tmp.resolve("absent.json")));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warningContains("no such file"), verdict.report());
        // The witness: the warning bearing on the code does indeed fall silent.
        assertTrue(verdict.warnings().stream()
            .noneMatch(message -> message.contains("already contains the quotes")),
            "-nowarn must silence what the processor found in the code: " + verdict.report());
    }

    @Test
    void faultyFile_stopsCompilation(@TempDir Path tmp) throws IOException {
        // The same file would stop the generation for the same reason: saying it here says it
        // early, which is the whole point of the processor.
        Path rules = writeRules(tmp, "{ \"color\": { \"Client\": \"red\" } }\n");

        Verdict verdict = compiler(tmp, List.of("-Aumlg.rules=" + rules));

        assertFalse(verdict.successful(), "A faulty rules file describes no diagram.");
        assertTrue(verdict.errorContains("unknown rule"), verdict.report());
    }

    @Test
    void silencedWarnings_alsoSilenceThoseTheOptionAddsBack(@TempDir Path tmp)
        throws IOException {
        // -Aumlg.warnings=none keeps its meaning: it silences everything, including what the option has just
        // makes audible. Errors, for their part, are never silenced.
        Path rules = writeRules(tmp, "{ \"strategy\": \"public\" }\n");

        Verdict verdict =
            compiler(tmp, List.of("-Aumlg.rules=" + rules, "-Aumlg.warnings=none"));

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }
}
