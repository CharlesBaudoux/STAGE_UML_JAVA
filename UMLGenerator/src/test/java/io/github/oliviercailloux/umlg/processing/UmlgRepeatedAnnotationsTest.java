package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmlgRepeatedAnnotationsTest {

    private static Verdict compiler(Path tmp, String body) throws IOException {
        return CompilationFixture.compileBody(tmp, body);
    }

    @Test
    void theSecondFaultyNote_isDesignatedByItsRank(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLNote(text = "Core of the domain")
            @UMLNote(text = "   ")
            @UMLNote(text = "Reread the contract")
            class Account {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("the 2nd @UMLNote on Account"), verdict.report());
    }

    @Test
    void theThirdFaultyNote_isDesignatedByItsRank(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLNote(text = "One")
            @UMLNote(text = "Two")
            @UMLNote(text = " ")
            class Account {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("the 3rd @UMLNote on Account"), verdict.report());
    }

    @Test
    void theFirstOfSeveral_isDesignatedByItsRank(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLNote(text = " ")
            @UMLNote(text = "Reread the contract")
            class Account {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("the 1st @UMLNote on Account"), verdict.report());
    }

    @Test
    void beyondTheTenth_theEnglishSuffixStaysRight(@TempDir Path tmp) throws IOException {
        StringBuilder fixture = new StringBuilder("@UMLClass\n");
        for (int rank = 1; rank <= 10; rank++) {
            fixture.append("@UMLNote(text = \"note ").append(rank).append("\")\n");
        }
        fixture.append("@UMLNote(text = \"  \")\nclass Account {}\n");

        Verdict verdict = compiler(tmp, fixture.toString());

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("the 11th @UMLNote on Account"), verdict.report());
    }

    @Test
    void aLoneNote_isGivenNoRank(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLNote(text = "  ")
            class Account {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("@UMLNote on Account"), verdict.report());
        assertTrue(verdict.errors().stream().noneMatch(message -> message.contains("the 1st")),
            verdict.report());
    }

    @Test
    void theSecondFaultyConstraint_isDesignatedByItsRank(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLConstraint(rule = "holder major")
            @UMLConstraint(rule = "{balance >= 0}")
            class Account {}
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warningContains("the 2nd @UMLConstraint on Account"),
            verdict.report());
    }

    @Test
    void theSecondFaultyDependency_isDesignatedByItsRank(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Service {}

            @UMLClass
            @UMLDependency(target = Service.class)
            @UMLDependency(target = Client.class)
            class Client {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("the 2nd @UMLDependency on Client"), verdict.report());
        assertTrue(verdict.errorContains("cannot depend on itself"), verdict.report());
    }
}
