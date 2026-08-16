package io.github.oliviercailloux.umlg.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.processing.CompilationFixture.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// What the processor says of @UMLPackage and @UMLAssociationClass.
class UmlgGroupsAndAssociationClassesTest {

    // The header every fixture shares lives in CompilationFixture, with the harness.

    private static Verdict compiler(Path tmp, String body) throws IOException {
        return CompilationFixture.compileBody(tmp, body);
    }

    // --- Le cas nominal ---

    @Test
    void correctCode_compilesWithoutReportingAnything(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLPackage(name = "Billing", color = "#EEEEEE")
            class Invoice {}

            @UMLClass
            @UMLPackage(name = "Billing", color = "#EEEEEE")
            class Line {}

            @UMLClass
            class Engine {}

            @UMLClass
            @UMLAssociationClass(source = Invoice.class, target = Engine.class)
            class Rattachement {}
            """);

        assertTrue(verdict.successful(), verdict.report());
        assertTrue(verdict.warnings().isEmpty(), verdict.report());
    }

    // --- @UMLPackage ---

    @Test
    void groupOnAnUnannotatedClass_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLPackage(name = "Billing")
            class OutsideDiagram {}
            """);

        assertFalse(verdict.successful(), "There is no box to file.");
        assertTrue(verdict.errorContains("does not carry @UMLClass"), verdict.report());
    }

    @Test
    void blankGroupName_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLPackage(name = "   ")
            class Invoice {}
            """);

        assertFalse(verdict.successful(), "A group is identified by its name.");
        assertTrue(verdict.errorContains("name()"), verdict.report());
    }

    @Test
    void unknownGroupColour_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLPackage(name = "Billing", color = "NotAColor")
            class Invoice {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        // The message is ColorCodec's, which queries the real catalogue of the engine: the
        // the question asked is "does PlantUML know this colour?", and not "does it have the
        // form?" — "NotAColor" has exactly that form.
        assertTrue(verdict.errorContains("unknown to PlantUML"), verdict.report());
    }

    @Test
    void twoColoursForOneGroup_areReported(@TempDir Path tmp) throws IOException {
        // A rectangle has only one colour: the author wrote the second one for nothing.
        Verdict verdict = compiler(tmp, """
            @UMLClass
            @UMLPackage(name = "Billing", color = "#EEEEEE")
            class Invoice {}

            @UMLClass
            @UMLPackage(name = "Billing", color = "#123456")
            class Line {}
            """);

        assertTrue(verdict.successful(), "The diagram stays right, only the colour changes.");
        assertTrue(verdict.warningContains("already coloured"), verdict.report());
    }

    // --- @UMLAssociationClass ---

    @Test
    void endpointOutsideTheDiagram_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Etudiant {}

            class Course {}

            @UMLClass
            @UMLAssociationClass(source = Etudiant.class, target = Course.class)
            class Inscription {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("does not carry @UMLClass"), verdict.report());
    }

    @Test
    void associationClassAsItsOwnEndpoint_stopsCompilation(@TempDir Path tmp)
        throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Course {}

            @UMLClass
            @UMLAssociationClass(source = Inscription.class, target = Course.class)
            class Inscription {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        // The message names the faulty endpoint, and it is AssociationClassExtractor's: the
        // the rule now exists in one place only, and both halves check it in the same
        // order — reflexivity before membership of the diagram.
        assertTrue(verdict.errorContains("source() is the association class itself"),
            verdict.report());
        assertTrue(verdict.errorContains("Point source() at one of the two classes"),
            verdict.report());
    }

    @Test
    void unannotatedAssociationClass_stopsCompilation(@TempDir Path tmp) throws IOException {
        Verdict verdict = compiler(tmp, """
            @UMLClass
            class Etudiant {}

            @UMLClass
            class Course {}

            @UMLAssociationClass(source = Etudiant.class, target = Course.class)
            class Inscription {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errorContains("does not carry @UMLClass"), verdict.report());
    }

    @Test
    void bothFaultyEndpoints_areReportedTogether(@TempDir Path tmp) throws IOException {
        // Recompiling to discover the second fault would be an avoidable waste of time.
        Verdict verdict = compiler(tmp, """
            class Etudiant {}

            class Course {}

            @UMLClass
            @UMLAssociationClass(source = Etudiant.class, target = Course.class)
            class Inscription {}
            """);

        assertFalse(verdict.successful(), verdict.report());
        assertTrue(verdict.errors().size() >= 2,
            "Both endpoints must be named: " + verdict.report());
    }
}
