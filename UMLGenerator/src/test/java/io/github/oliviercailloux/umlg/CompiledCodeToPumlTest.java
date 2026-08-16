package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * From a directory of {@code .class} files to the delivered {@code .puml} files.
 *
 * <p>WHAT THIS TEST COVERS AND THE OTHERS DO NOT
 *
 * <p>The whole chain was already exercised, but in two disjoint halves.
 * {@code UMLGeneratorIntegrationTest} compiles a real project and goes to the image, on a fixture
 * of a single class with no relation and no view. {@code PlantUmlWrittenViewsTest} exercises the
 * output — the full view at the root, the views in a subdirectory, the source next to each
 * image — but from a model built by hand, never reading a {@code .class}.
 *
 * <p>Between the two the joint remained: a compiled project whose rules file declares
 * views, whose classes carry real relations, and whose produced {@code .puml} files are read.
 * This is where the decisions that exist only once everything is assembled live — the merging of
 * reciprocal associations, the navigation links from one view to another, the italics of a
 * an abstract operation read by reflection.
 *
 * <p>WHY THE .PUML, AND NOT THE SVG
 *
 * <p>It is the file that is read back, touched up and versioned, and the one whose diff says
 * something. The SVG answers "did the drawing come out", the {@code .puml} answers "did the library
 * did it write what was needed" — the only one of the two questions whose answer can be read.
 */
class CompiledCodeToPumlTest {

    /**
     * A whole project in a single file: a hierarchy, a pair of reciprocal fields,
     * an abstract operation, and a class nobody annotated.
     *
     * <p>Everything fits in one file because javac accepts several top-level types
     * as soon as a single one is public, and one compilation is then enough where several would be
     * four.
     */
    private static final String Fixture = """
        package com.school;

        import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
        import io.github.oliviercailloux.umlg.annotation.UMLClass;
        import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
        import java.util.List;

        @UMLClass(strategy = UMLVisibility.PUBLIC)
        public abstract class Person {
            public String name;

            public abstract void validate();
        }

        @UMLClass(strategy = UMLVisibility.PUBLIC)
        class Student extends Person {
            @UMLAssociation(targetMultiplicity = "0..*", targetRole = "course")
            public List<Course> course;

            @Override
            public void validate() {}
        }

        @UMLClass(strategy = UMLVisibility.PUBLIC)
        class Course {
            public String intitule;

            @UMLAssociation(targetMultiplicity = "1..*", targetRole = "registered")
            public List<Student> registered;
        }

        class Intendance {}
        """;

    /** The project rules, as the build copies them to the root of the classes. */
    private static final String Rules = """
        {
          "title": "École",
          "views": [ { "name": "Tuition", "retain": ["Student", "Course"] } ]
        }
        """;

    /**
     * Writes the fixture, compiles it for real, and puts the rules file where the scan
     * looks: at the root of the analysed directory.
     *
     * @param tmp the directory of the test
     * @return the directory of compiled classes, ready to be analysed
     */
    private static Path compiledProject(Path tmp) throws IOException {
        Path classes = tmp.resolve("classes");
        CompilationWithoutProcessor.compiler(tmp.resolve("src"), classes,
            "com/school/Person.java", Fixture);
        Files.writeString(classes.resolve("umlg.json"), Rules, StandardCharsets.UTF_8);
        return classes;
    }

    /** Generates the diagram directory of the fixture, and returns the source of the overview view. */
    private static Path generate(Path tmp) throws IOException {
        Path classes = compiledProject(tmp);
        Path folder = tmp.resolve("diagrams");

        UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputDirectory(folder.toString())
            .build()
            .generate();

        return folder;
    }

    private static String read(Path file) throws IOException {
        assertTrue(Files.exists(file), "Expected file is missing: " + file);
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    // --- What the directory holds ---

    @Test
    void theDirectory_carriesTheFullViewItsViewAndEachImageSource(@TempDir Path tmp)
        throws IOException {
        Path folder = generate(tmp);

        // The full view at the root: it is the one opened first, and looking for it among the
        // others would be a chore.
        assertTrue(Files.exists(folder.resolve("project.puml")));
        assertTrue(Files.exists(folder.resolve("project.svg")));

        // The view declared by the rules file, in the subdirectory, with its source.
        assertTrue(Files.exists(folder.resolve("views/tuition.puml")));
        assertTrue(Files.exists(folder.resolve("views/tuition.svg")));

        // The class nobody annotated is counted and named, not passed over in silence.
        String ignored = read(folder.resolve(UMLGenerator.CLASSES_IGNORED_FILE));
        assertTrue(ignored.contains("Intendance"), ignored);
    }

    @Test
    void theOverviewView_pointsToTheDetailedView(@TempDir Path tmp) throws IOException {
        // A diagram directory without links is but a collection of unrelated images.
        String project = read(generate(tmp).resolve("project.puml"));

        assertTrue(project.contains("[[views/tuition.svg]]"),
            "The boxes of the view must point to it:\n" + project);
    }

    @Test
    void theRulesFileTitle_reachesTheSource(@TempDir Path tmp) throws IOException {
        // A rule written by hand, read at the root of the analysed directory, and found again in the
        // the delivered file: that is the whole journey in one assertion.
        assertTrue(read(generate(tmp).resolve("project.puml")).contains("title École"));
    }

    // --- What the source says of the model ---

    @Test
    void twoReciprocalFields_giveASingleStrokeInTheDeliveredFile(@TempDir Path tmp)
        throws IOException {
        // The joint nothing covered: the merge is computed on a model drawn from real
        // compiled classes, then written by the writer in the delivered file. Student.course and
        // Course.registered face each other; UML wants a single stroke, with two named endpoints.
        List<String> strokes = PlantUmlAssert.linkLines(read(generate(tmp)
            .resolve("views/tuition.puml")));

        assertEquals(1, strokes.size(),
            "One only stroke expected between Student and Course, obtenu : " + strokes);
        assertTrue(strokes.get(0).contains("\"registered 1..*\""), strokes.get(0));
        assertTrue(strokes.get(0).contains("\"course 0..*\""), strokes.get(0));
    }

    @Test
    void inheritanceAndAbstractOperation_areReadInTheCode(@TempDir Path tmp) throws IOException {
        // Neither one nor the other is declared by an annotation: both are read in the
        // .class, and must survive as far as the delivered file.
        String project = read(generate(tmp).resolve("project.puml"));

        assertTrue(project.contains("Person <|-- Student"),
            "The generalization must be read from extends:\n" + project);
        assertTrue(project.contains("{abstract} validate()"),
            "UML writes an operation without a body in italics:\n" + project);
        assertTrue(project.contains("abstract class \"Person\""),
            "The box keyword must say what the class is:\n" + project);
    }

    // --- What the delivered file is worth ---

    @Test
    void theDeliveredSources_areAcceptedByPlantUml(@TempDir Path tmp) throws IOException {
        // The file is set next to the image to be read again and touched up: it must therefore be
        // valid PlantUML on its own, and not merely a string holding the right words.
        Path folder = generate(tmp);

        PlantUmlAssert.assertCompile(read(folder.resolve("project.puml")));
        PlantUmlAssert.assertCompile(read(folder.resolve("views/tuition.puml")));
    }

    @Test
    void twoGenerations_writeTheSameFile(@TempDir Path tmp) throws IOException {
        // The diagram is made to be versioned: two generations of the same code must
        // return the same text, otherwise each run would pollute the diff. Nothing
        // guarantees for free — neither Files.walk, nor getDeclaredFields, nor a hash table
        // have a defined order.
        String first = read(generate(tmp).resolve("project.puml"));
        String second = read(generate(tmp.resolve("bis")).resolve("project.puml"));

        assertEquals(first, second);
    }

    @Test
    void noDeliveredSource_containsAnErrorMessage(@TempDir Path tmp) throws IOException {
        // The safety net of the safety net: PlantUML is lenient enough to draw
        // an error image reporting nothing, and that is the worst of outcomes — nothing tells it
        // tells apart from a successful diagram.
        String project = read(generate(tmp).resolve("project.puml"));

        assertFalse(project.toLowerCase().contains("syntax error"), project);
        assertTrue(project.startsWith("@startuml"), project);
        assertTrue(project.stripTrailing().endsWith("@enduml"), project);
    }
}
