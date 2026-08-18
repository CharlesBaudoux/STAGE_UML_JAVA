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
 * <p>Covers the joint the other tests leave open: {@code UMLGeneratorIntegrationTest} compiles a
 * real project but on a single class with no relation and no view, and
 * {@code PlantUmlWrittenViewsTest} exercises the output from a model built by hand. Here a
 * compiled project declares views and carries real relations, and the produced {@code .puml}
 * files are read back — merged reciprocal associations, navigation links between views, italics
 * on abstract operations.
 *
 * <p>The {@code .puml} rather than the SVG: it is the file that is read back, touched up and
 * versioned, and the only one of the two whose diff can be read.
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

    @Test
    void theDirectory_carriesTheFullViewItsViewAndEachImageSource(@TempDir Path tmp)
        throws IOException {
        Path folder = generate(tmp);

        assertTrue(Files.exists(folder.resolve("project.puml")));
        assertTrue(Files.exists(folder.resolve("project.svg")));

        assertTrue(Files.exists(folder.resolve("views/tuition.puml")));
        assertTrue(Files.exists(folder.resolve("views/tuition.svg")));

        String ignored = read(folder.resolve(UMLGenerator.CLASSES_IGNORED_FILE));
        assertTrue(ignored.contains("Intendance"), ignored);
    }

    @Test
    void theOverviewView_pointsToTheDetailedView(@TempDir Path tmp) throws IOException {
        String project = read(generate(tmp).resolve("project.puml"));

        assertTrue(project.contains("[[views/tuition.svg]]"),
            "The boxes of the view must point to it:\n" + project);
    }

    @Test
    void theRulesFileTitle_reachesTheSource(@TempDir Path tmp) throws IOException {
        assertTrue(read(generate(tmp).resolve("project.puml")).contains("title École"));
    }

    @Test
    void twoReciprocalFields_giveASingleStrokeInTheDeliveredFile(@TempDir Path tmp)
        throws IOException {
        List<String> strokes = PlantUmlAssert.linkLines(read(generate(tmp)
            .resolve("views/tuition.puml")));

        assertEquals(1, strokes.size(),
            "One only stroke expected between Student and Course, obtenu : " + strokes);
        assertTrue(strokes.get(0).contains("\"registered 1..*\""), strokes.get(0));
        assertTrue(strokes.get(0).contains("\"course 0..*\""), strokes.get(0));
    }

    @Test
    void inheritanceAndAbstractOperation_areReadInTheCode(@TempDir Path tmp) throws IOException {
        String project = read(generate(tmp).resolve("project.puml"));

        assertTrue(project.contains("Person <|-- Student"),
            "The generalization must be read from extends:\n" + project);
        assertTrue(project.contains("{abstract} validate()"),
            "UML writes an operation without a body in italics:\n" + project);
        assertTrue(project.contains("abstract class \"Person\""),
            "The box keyword must say what the class is:\n" + project);
    }

    @Test
    void theDeliveredSources_areAcceptedByPlantUml(@TempDir Path tmp) throws IOException {
        Path folder = generate(tmp);

        PlantUmlAssert.assertCompile(read(folder.resolve("project.puml")));
        PlantUmlAssert.assertCompile(read(folder.resolve("views/tuition.puml")));
    }

    @Test
    void twoGenerations_writeTheSameFile(@TempDir Path tmp) throws IOException {
        String first = read(generate(tmp).resolve("project.puml"));
        String second = read(generate(tmp.resolve("bis")).resolve("project.puml"));

        assertEquals(first, second);
    }

    @Test
    void noDeliveredSource_containsAnErrorMessage(@TempDir Path tmp) throws IOException {
        String project = read(generate(tmp).resolve("project.puml"));

        assertFalse(project.toLowerCase().contains("syntax error"), project);
        assertTrue(project.startsWith("@startuml"), project);
        assertTrue(project.stripTrailing().endsWith("@enduml"), project);
    }
}
