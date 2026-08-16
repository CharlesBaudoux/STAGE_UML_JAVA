package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// A diagnostic on a repeated annotation says which one.
//
// WHAT THESE TESTS PROTECT
//
// When the user writes several @UMLNote on the same class, javac wraps them in a
// container it builds itself — @UMLNotes — and which exists nowhere in the file
// source. This container therefore has no position: the diagnostic sits on the declaration of
// whole class, and out of five notes of which only one is faulty, nothing says which. The API of
// processors gives no way of finding the lost place again.
//
// The text is the only place left to say it. The rank is written only when there is
// several: on a single annotation, javac locates it perfectly, and "the 1st"
// would be nothing but noise — that is half of what these tests check.
class UmlgRepeatedAnnotationsTest {

    // The header every fixture shares lives in CompilationFixture, with the harness.

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
        // The ordinal suffix is not "nd" for everyone: the English rule is checked
        // beyond the second.
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
        // It lost its position like the others, once wrapped: the rank
        // is therefore due, "the 1st" included.
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
        // "11st" does not exist: the English rule makes an exception of eleven, twelve and thirteen, and
        // a message the user reads has no right to mangle its own language.
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
        // It is perfectly located by javac: a rank would teach nothing, and would weigh down
        // every message of the current case.
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
