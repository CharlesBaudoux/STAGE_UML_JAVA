package io.github.oliviercailloux.umlg;

import static io.github.oliviercailloux.umlg.CompilationWithoutProcessor.compiler;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.ProjectScanner;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import io.github.oliviercailloux.umlg.rendering.PlantUmlWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

// The whole library, from the annotated code to the delivered file.
//
// Three families of tests:
//   - the whole chain: a real Java project is written, compiled, scanned, and the
//     produced SVG, and it is checked there that the decisions taken upstream (@UMLExclude, the
//     noise control, strategy) hold all the way to the image;
//   - a global invariant over target/test-classes: whatever fixture a future test
//     adds, the whole must go on producing valid PlantUML with no alias in
//     double ;
//   - the agreement between the Builder and the writer on extensions: what one accepts, the other
//     must be able to render it — a past disagreement between them is the origin of these tests.
class UMLGeneratorIntegrationTest {

    // Magic number of a PNG: used to check the format actually written.
    private static final byte[] HEADER_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    // The javac harness — write a .java, compile it for real, scan the output
    // output — lives in CompilationWithoutProcessor, statically imported above.

    // --- Whole chain ---

    @Test
    void annotatedProject_producesAnSvgHoldingTheArchitecture(@TempDir Path tmp) throws Exception {
        // The nominal scenario of the library, end to end.
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/bank/Account.java", """
            package com.bank;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLExclude;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(stereotype = "Entity", color = "#DDEEFF", strategy = UMLVisibility.PRIVATE)
            public class Account {
                private double balance;

                @UMLExclude
                private String traceInterne;

                public void deposit(double amount) {}
                public double getSolde() { return balance; }
            }
            """);
        // The rules file, set at the root of the analysed directory — where the build
        // Maven would copy it. Without it, no accessor would be hidden: that is precisely what
        // which this test must prove end to end, from the hand-written file to the SVG.
        Files.writeString(classes.resolve("umlg.json"), "{ \"hide\": [\"get*\"] }\n",
            StandardCharsets.UTF_8);

        Path output = tmp.resolve("architecture.svg");

        UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(output.toString())
            .build()
            .generate();

        String svg = Files.readString(output, StandardCharsets.UTF_8);

        assertFalse(svg.toLowerCase().contains("syntax error"),
            "The delivered diagram is an error image:\n" + svg);
        assertTrue(svg.contains("Account"), "The annotated class must appear.");
        assertTrue(svg.contains("balance"), "The private field must appear (strategy PRIVATE).");
        assertTrue(svg.contains("deposit"), "The business method must appear.");
        assertTrue(svg.contains("Entity"), "The stereotype must appear.");
        assertFalse(svg.contains("traceInterne"),
            "@UMLExclude must hide the field all the way to the SVG.");
        assertFalse(svg.contains("getSolde"),
            "\"hide get*\" must hold from the rules file to the SVG.");
    }

    @Test
    void projectWithoutAnyAnnotation_failsWithAUsefulMessage(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/bank/Neutral.java", """
            package com.bank;

            public class Neutral {}
            """);
        Path output = tmp.resolve("architecture.svg");

        UMLGenerator generator = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(output.toString())
            .build();

        RuntimeException e = assertThrows(RuntimeException.class, generator::generate);

        // generate() wraps every error: the message must make the diagnosis possible.
        assertTrue(e.getMessage().contains(classes.toString()),
            "The source directory must appear in the message: " + e.getMessage());
        assertTrue(e.getMessage().contains(output.toString()),
            "The output file must appear in the message: " + e.getMessage());
        assertNotNull(e.getCause(), "The original cause must be kept.");
        assertFalse(Files.exists(output), "No file must be written on failure.");
    }

    @Test
    void generate_returnsTheReport_andNamesEveryIgnoredClassInAFile(@TempDir Path tmp)
        throws Exception {
        // The returned report stops naming beyond a dozen; the file written in the
        // the output directory does not have that limit — it is there to be opened when the count has
        // raised the alarm.
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/bank/Account.java", """
            package com.bank;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Account {}

            class Forgotten {}

            class Dropped {}
            """);
        Path folder = tmp.resolve("diagrams");

        GenerationReport report = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputDirectory(folder.toString())
            .build()
            .generate();

        assertEquals(1, report.classesRead());
        assertEquals(List.of("Dropped", "Forgotten"), report.classesIgnored());

        Path list = folder.resolve(UMLGenerator.CLASSES_IGNORED_FILE);
        assertTrue(Files.exists(list), "The complete list must be written in the directory.");
        String content = Files.readString(list, StandardCharsets.UTF_8);
        assertTrue(content.contains("Forgotten") && content.contains("Dropped"), content);

        // The .puml source goes with the image, ready to be touched up or compared.
        String puml = Files.readString(folder.resolve("project.puml"), StandardCharsets.UTF_8);
        assertTrue(puml.startsWith("@startuml"), puml);
        assertTrue(puml.contains("Account"), puml);
    }

    @Test
    void inSingleFileMode_theListIsWrittenToo(@TempDir Path tmp) throws Exception {
        // Single-file mode had no test on that point, although it is the one used by
        // the user who configured nothing — and hence the one who most needs to learn
        // which classes are not on their drawing.
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/bank/Account.java", """
            package com.bank;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Account {}

            class Forgotten {}
            """);

        GenerationReport report = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(tmp.resolve("archi.svg").toString())
            .build()
            .generate();

        Path list = tmp.resolve(UMLGenerator.CLASSES_IGNORED_FILE);
        assertTrue(Files.exists(list),
            "The list must go with the image, in single-file mode as in directory mode.");
        assertTrue(Files.readString(list, StandardCharsets.UTF_8).contains("Forgotten"));

        // And the returned report names the requested file, not the directory holding it.
        assertTrue(report.summary().contains("archi.svg"), report.summary());
    }

    @Test
    void withoutIgnoredClass_theStaleFileIsRemoved(@TempDir Path tmp) throws Exception {
        // A list left by a previous generation would lie all the better for having
        // look in place.
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/bank/Account.java", """
            package com.bank;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Account {}
            """);
        Path folder = tmp.resolve("diagrams");
        Files.createDirectories(folder);
        Path list = folder.resolve(UMLGenerator.CLASSES_IGNORED_FILE);
        Files.writeString(list, "Perimee\n", StandardCharsets.UTF_8);

        UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputDirectory(folder.toString())
            .build()
            .generate();

        assertFalse(Files.exists(list), "The stale list must vanish.");
    }

    @Test
    void theKnownColours_areExposedByTheFacade() {
        // The catalogue was locked in a non-exported package: the user whose
        // colour was refused could not ask which ones exist.
        var colors = UMLGenerator.knownColors();

        assertTrue(colors.contains("LightBlue"), "The PlantUML catalogue must be exposed.");
        assertTrue(colors.contains("red"));
    }

    @Test
    void writeFailure_isWrappedWithTheContext(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/bank/Account.java", """
            package com.bank;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Account {}
            """);

        UMLGenerator generator = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(tmp.resolve("folder/absent/architecture.svg").toString())
            .build();

        RuntimeException e = assertThrows(RuntimeException.class, generator::generate);
        assertTrue(e.getMessage().contains("generate the UML diagram"), e.getMessage());
    }

    @Test
    void repeatedGeneration_isIdempotent(@TempDir Path tmp) throws Exception {
        // Running the generator again on an unchanged project must neither fail nor produce a
        // a different diagram.
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/bank/Account.java", """
            package com.bank;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Account {
                public double balance;
                public void deposit(double amount) {}
            }
            """);
        Path output = tmp.resolve("architecture.svg");

        UMLGenerator generator = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(output.toString())
            .build();

        generator.generate();
        String first = Files.readString(output, StandardCharsets.UTF_8);
        generator.generate();
        String second = Files.readString(output, StandardCharsets.UTF_8);

        assertTrue(second.contains("Account"));
        assertEquals(first, second,
            "Two identical generations must give the same SVG.");
    }

    // --- Global invariant over the project fixtures ---

    @Test
    void allProjectFixtureClasses_produceAValidDiagram() throws Exception {
        // Safety net: whatever fixture a future test adds, the whole
        // of target/test-classes must go on producing PlantUML the engine accepts.
        // It is this test that catches empty or colliding aliases, which no assert on the
        // the file size cannot see.
        UmlProject project = ProjectScanner.scan(Paths.get("target", "test-classes")).project();

        String source = PlantUmlAssert.documentFor(project.entities().toArray(new UmlEntity[0]));

        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void aliasesOfProjectFixtures_areAllUnique() throws Exception {
        UmlProject project = ProjectScanner.scan(Paths.get("target", "test-classes")).project();
        String source = PlantUmlAssert.documentFor(project.entities().toArray(new UmlEntity[0]));

        List<String> alias = PlantUmlAssert.aliasDeclared(source);

        assertEquals(project.entities().size(), alias.size(),
            "Every scanned entity must give one class declaration.");
        assertEquals(alias.size(), alias.stream().distinct().count(),
            "Duplicate aliases would merge distinct classes: " + alias);
    }

    // --- Builder ---

    @ParameterizedTest
    @ValueSource(strings = {"a.svg", "a.SVG", "a.Svg", "a.png", "a.PNG", "a.pNg",
        "MY.DIAGRAM.SVG"})
    void builder_acceptsExtensionsWhateverTheCase(String name, @TempDir Path tmp) {
        // The Builder refused "Diagram.SVG" while PlantUmlWriter could render it:
        // both compared the extension on their own side, one without normalizing the case.
        assertDoesNotThrow(
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile(name).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.jpg", "a.pdf", "a.txt", "diagram", "svg", "a.svg.bak", "a.s vg"})
    void builder_stillRefusesUnsupportedFormats(String name, @TempDir Path tmp) {
        // Hardening the case must not turn the filter into a sieve.
        assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile(name));
    }

    @Test
    void builder_namesTheFaultyFileInItsMessage(@TempDir Path tmp) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile("report.pdf"));

        assertTrue(e.getMessage().contains("report.pdf"), e.getMessage());
    }

    // @CsvSource: each line provides two arguments, the file name and the expected format.
    @ParameterizedTest
    @CsvSource({"a.svg, SVG", "A.SVG, SVG", "a.Svg, SVG", "a.png, PNG", "A.PNG, PNG", "a.pNg, PNG"})
    void everyNameTheBuilderAccepts_isRenderedInItsExtensionFormat(
        String name, String expectedFormat, @TempDir Path tmp) throws Exception {
        // The real invariant, the one keeping the two components from parting again: what the
        // Builder lets through, the writer must render it in the format announced by
        // the extension — case included, and with no silent fallback to PNG.
        UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile(name).build();

        Path output = tmp.resolve(name);
        PlantUmlWriter.write(UmlProject.given(Set.of(EntityExtractor.extract(Fixture.class))),
            output.toString());

        if (expectedFormat.equals("SVG")) {
            assertTrue(Files.readString(output, StandardCharsets.UTF_8).contains("<svg"),
                name + " must produce SVG.");
        } else {
            byte[] header = Arrays.copyOf(Files.readAllBytes(output), HEADER_PNG.length);
            assertArrayEquals(HEADER_PNG, header, name + " must produce PNG.");
        }
    }

    @Test
    void fullGenerationWithUppercaseExtension_reallyProducesSvg(@TempDir Path tmp)
        throws Exception {
        // The whole chain, not validation only: ".SVG" crosses the Builder,
        // the scan and the writer.
        Path classes = tmp.resolve("classes");
        compiler(tmp.resolve("src"), classes, "com/bank/Account.java", """
            package com.bank;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Account {}
            """);
        Path output = tmp.resolve("ARCHITECTURE.SVG");

        UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .outputFile(output.toString())
            .build()
            .generate();

        String content = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(content.contains("<svg"), "The delivered file must be SVG, not PNG.");
        assertTrue(content.contains("Account"));
    }

    @Test
    void supports_refusesANullName() {
        assertThrows(NullPointerException.class, () -> PlantUmlWriter.supports(null));
    }

    @UMLClass(name = "Fixture")
    static class Fixture {}

    @Test
    void builder_refusesANullFileName(@TempDir Path tmp) {
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile(null));
    }

    @Test
    void builder_refusesANullSourcePath() {
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().sourceDirectory(null));
    }

    @Test
    void builder_refusesAFileAsSourceDirectory(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("not-un-folder.txt");
        Files.writeString(file, "x");

        assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory(file.toString()));
    }

    @Test
    void builder_acceptsBothSupportedFormats(@TempDir Path tmp) {
        assertDoesNotThrow(() -> UMLGenerator.builder()
            .sourceDirectory(tmp.toString()).outputFile("a.svg").build());
        assertDoesNotThrow(() -> UMLGenerator.builder()
            .sourceDirectory(tmp.toString()).outputFile("a.png").build());
    }

    // The builder is not consumed by build(): several generators can be drawn from it, by
    // changing only the output file.
    @Test
    void builder_isReusableForSeveralGenerators(@TempDir Path tmp) {
        UMLGenerator.Builder builder = UMLGenerator.builder().sourceDirectory(tmp.toString());

        assertDoesNotThrow(() -> {
            builder.outputFile("a.svg").build();
            builder.outputFile("b.png").build();
        });
    }
}
