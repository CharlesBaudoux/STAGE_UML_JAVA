package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UMLGeneratorTest {

    private static final String THEME_UMLG_MARKER = "skinparam ClassBorderColor #33526E";

    @UMLClass(name = "FacadeFixture", strategy = UMLVisibility.PUBLIC)
    static class FacadeFixture {
        public String name;
    }

    private static Path aDirectoryOfOneClass(Path tmp) throws IOException {
        String path = FacadeFixture.class.getName().replace('.', '/') + ".class";
        Path root = tmp.resolve("classes");
        Path destination = root.resolve(path);
        Files.createDirectories(destination.getParent());
        Files.copy(Path.of("target", "test-classes").resolve(path), destination);
        return root;
    }

    private static UMLGenerator.Builder onFixture(Path tmp) throws IOException {
        return UMLGenerator.builder().sourceDirectory(aDirectoryOfOneClass(tmp).toString());
    }

    @Test
    void withoutSourceDirectory_theBuilderRefusesToBuild() {
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().outputFile("test.png").build(),
            "The source directory has no reasonable default.");
    }

    @Test
    void aMissingSourceDirectory_isRefusedRightAtTheCall() {
        assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory("/un/path/totalement/invente"));
    }

    @Test
    void anUnknownExtension_isRefusedRightAtTheCall(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).outputFile("d.jpg"));
    }

    @Test
    void aNullStyle_isRefused(@TempDir Path tmp) {
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).style(null));
    }

    @Test
    void anOutputFileAndDirectory_areRefusedTogether(@TempDir Path tmp) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> UMLGenerator.builder()
                .sourceDirectory(tmp.toString())
                .outputFile("archi.png")
                .outputDirectory("docs")
                .build());

        assertTrue(e.getMessage().contains("archi.png") && e.getMessage().contains("docs"),
            "The message must name both requested outputs: " + e.getMessage());
    }

    @Test
    void aFormatRequestedWithoutDirectory_isRefused(@TempDir Path tmp) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> UMLGenerator.builder()
                .sourceDirectory(tmp.toString())
                .outputFile("a.png")
                .outputFormat("svg")
                .build());

        assertTrue(e.getMessage().contains("svg"), e.getMessage());
    }

    @Test
    void keepingThePreviousGenerationWithoutDirectory_isRefused(@TempDir Path tmp) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> UMLGenerator.builder()
                .sourceDirectory(tmp.toString())
                .outputFile("archi.svg")
                .keepPrevious(true)
                .build());

        assertTrue(e.getMessage().contains("keepPrevious"), e.getMessage());
    }

    @Test
    void notKeepingThePreviousGeneration_staysValidWithoutDirectory(@TempDir Path tmp) {
        assertDoesNotThrow(() -> UMLGenerator.builder()
            .sourceDirectory(tmp.toString())
            .outputFile("archi.svg")
            .keepPrevious(false)
            .build());
    }

    @Test
    void aFormatRequestedWithADirectory_isApplied(@TempDir Path tmp) throws IOException {
        Path folder = tmp.resolve("diagrams");

        GenerationReport report = onFixture(tmp)
            .outputDirectory(folder.toString())
            .outputFormat("png")
            .build()
            .generate();

        assertEquals(List.of(folder.resolve("project.png")), report.files());
    }

    @Test
    void theDefaultFile_neverCountsAsARequest(@TempDir Path tmp) throws IOException {
        Path folder = tmp.resolve("diagrams");

        GenerationReport report = onFixture(tmp)
            .outputDirectory(folder.toString())
            .build()
            .generate();

        assertEquals(List.of(folder.resolve("project.svg")), report.files());
    }

    @Test
    void viewsInSingleFileMode_areReported(@TempDir Path tmp) throws IOException {
        Path source = aDirectoryOfOneClass(tmp);
        Files.writeString(source.resolve("umlg.json"), "{ \"viewsByPackage\": true }",
            StandardCharsets.UTF_8);

        GenerationReport report = UMLGenerator.builder()
            .sourceDirectory(source.toString())
            .outputFile(tmp.resolve("archi.svg").toString())
            .build()
            .generate();

        assertTrue(report.warnings().stream().anyMatch(a -> a.contains("partial view")),
            "The lost views must be said: " + report.warnings());
    }

    @Test
    void viewsInDirectoryMode_areNotReported(@TempDir Path tmp) throws IOException {
        Path source = aDirectoryOfOneClass(tmp);
        Files.writeString(source.resolve("umlg.json"), "{ \"viewsByPackage\": true }",
            StandardCharsets.UTF_8);

        GenerationReport report = UMLGenerator.builder()
            .sourceDirectory(source.toString())
            .outputDirectory(tmp.resolve("diagrams").toString())
            .build()
            .generate();

        assertEquals(List.of(), report.warnings(),
            "Nothing is lost, so there is nothing to say.");
        assertEquals(2, report.files().size(), "The full view and the package view.");
    }

    @Test
    void generate_writesTheRequestedFileAndItsSource(@TempDir Path tmp) throws IOException {
        Path output = tmp.resolve("result.svg");

        GenerationReport report =
            onFixture(tmp).outputFile(output.toString()).build().generate();

        assertTrue(Files.exists(output), "The requested image must be written.");
        assertEquals(1, report.classesRead());
        assertTrue(Files.readString(tmp.resolve("result.puml"), StandardCharsets.UTF_8)
            .contains("FacadeFixture"), "The box of the fixture must be in the source.");
    }

    @Test
    void umlgStyle_setsTheLibraryThemeInTheSource(@TempDir Path tmp) throws IOException {
        onFixture(tmp)
            .outputFile(tmp.resolve("theme.svg").toString())
            .style(UmlStyle.UMLG)
            .build()
            .generate();

        assertTrue(Files.readString(tmp.resolve("theme.puml"), StandardCharsets.UTF_8)
            .contains(THEME_UMLG_MARKER));
    }

    @Test
    void defaultStyle_leavesThePlantUmlAppearance(@TempDir Path tmp) throws IOException {
        onFixture(tmp).outputFile(tmp.resolve("nu.svg").toString()).build().generate();

        assertFalse(Files.readString(tmp.resolve("nu.puml"), StandardCharsets.UTF_8)
            .contains(THEME_UMLG_MARKER), "Asking for nothing is imposing nothing.");
    }

    @Test
    void keepPrevious_writesTheNextGenerationAlongside(@TempDir Path tmp) throws IOException {
        Path folder = tmp.resolve("diagrams");
        UMLGenerator generator = onFixture(tmp)
            .outputDirectory(folder.toString())
            .keepPrevious(true)
            .build();

        generator.generate();
        GenerationReport second = generator.generate();

        assertTrue(Files.exists(folder.resolve("project.svg")),
            "The first generation must stay intact.");
        assertEquals(List.of(tmp.resolve("diagrams-2").resolve("project.svg")),
            second.files(), "The second must go to the numbered neighbouring directory.");
    }

    @Test
    void withoutKeepPrevious_theNextGenerationReplacesThePrevious(@TempDir Path tmp)
        throws IOException {
        Path folder = tmp.resolve("diagrams");
        UMLGenerator generator =
            onFixture(tmp).outputDirectory(folder.toString()).build();

        generator.generate();
        GenerationReport second = generator.generate();

        assertEquals(List.of(folder.resolve("project.svg")), second.files());
        assertFalse(Files.exists(tmp.resolve("diagrams-2")),
            "The directory describes the current state of the code, and nothing piles up alongside.");
    }

    @Test
    void withoutClasspath_aClassWhoseParentLivesInAJar_isUnreadable(@TempDir Path tmp)
        throws Exception {
        Path classes = aProjectWhoseParentIsInAJar(tmp);
        List<String> said = new ArrayList<>();

        RuntimeException e;
        try (Warnings.Channel channel = Warnings.redirectTo(said::add)) {
            e = assertThrows(RuntimeException.class, () -> UMLGenerator.builder()
                .sourceDirectory(classes.toString())
                .outputFile(tmp.resolve("archi.svg").toString())
                .build()
                .generate());
        }

        assertTrue(said.stream().anyMatch(a -> a.contains("appli.Child")),
            "The loading failure must be said, and name the class: " + said);
        assertTrue(e.getMessage().contains("nothing to draw"), e.getMessage());
    }

    @Test
    void withTheClasspath_aClassWhoseParentLivesInAJar_isDrawn(@TempDir Path tmp)
        throws Exception {
        Path classes = aProjectWhoseParentIsInAJar(tmp);

        GenerationReport report = UMLGenerator.builder()
            .sourceDirectory(classes.toString())
            .classpath(List.of(tmp.resolve("lib").resolve("tiers.jar").toString()))
            .outputFile(tmp.resolve("archi.svg").toString())
            .build()
            .generate();

        assertEquals(1, report.classesRead());
        assertTrue(Files.readString(tmp.resolve("archi.puml"), StandardCharsets.UTF_8)
            .contains("Child"), "The box of the inheriting class must be drawn.");
    }

    @Test
    void aNullClasspath_isRefused(@TempDir Path tmp) {
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).classpath(null));
    }

    private static Path aProjectWhoseParentIsInAJar(Path tmp) throws Exception {
        Path classes = tmp.resolve("compilees");
        CompilationWithoutProcessor.compiler(tmp.resolve("src"), classes,
            "tiers/Base.java", """
                package tiers;

                public class Base {}
                """,
            "appli/Child.java", """
                package appli;

                import io.github.oliviercailloux.umlg.annotation.UMLClass;
                import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

                @UMLClass(strategy = UMLVisibility.PUBLIC)
                public class Child extends tiers.Base {
                    public String name;
                }
                """);

        Path jar = tmp.resolve("lib").resolve("tiers.jar");
        Files.createDirectories(jar.getParent());
        String entry = "tiers/Base.class";
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(entry));
            output.write(Files.readAllBytes(classes.resolve(entry)));
            output.closeEntry();
        }
        Files.delete(classes.resolve(entry));
        Files.delete(classes.resolve("tiers"));
        return classes;
    }

    @Test
    void commandLine_withANameWithoutExtension_writesAViewDirectory(@TempDir Path tmp)
        throws Exception {
        Path output = tmp.resolve("diagrams");

        UMLGenerator.main(
            new String[] {aDirectoryOfOneClass(tmp).toString(), output.toString()});

        assertTrue(Files.isDirectory(output), "One name without extension requested un folder.");
        assertTrue(Files.exists(output.resolve("project.svg")), "The full view is written there.");
    }

    @Test
    void commandLine_withAnExtension_writesThatSingleFile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("architecture.svg");

        UMLGenerator.main(
            new String[] {aDirectoryOfOneClass(tmp).toString(), file.toString()});

        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void commandLine_acceptsThePngFormat(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("diagrams");

        UMLGenerator.main(
            new String[] {aDirectoryOfOneClass(tmp).toString(), output.toString(), "png"});

        assertTrue(Files.exists(output.resolve("project.png")));
    }

    @Test
    void aBareFileName_meansTheCurrentDirectory() {
        Path folder = UMLGenerator.outputFolder(Path.of("diagram.svg"));

        assertEquals(Path.of(""), folder);
        assertEquals(Path.of(UMLGenerator.CLASSES_IGNORED_FILE),
            folder.resolve(UMLGenerator.CLASSES_IGNORED_FILE));
    }

    @Test
    void aBareNameAndTheSameAsDotSlash_leadToTheSameFile() {
        Path nu = UMLGenerator.outputFolder(Path.of("diagram.svg"))
            .resolve(UMLGenerator.CLASSES_IGNORED_FILE);
        Path pointSlash = UMLGenerator.outputFolder(Path.of("./diagram.svg"))
            .resolve(UMLGenerator.CLASSES_IGNORED_FILE);

        assertEquals(nu.toAbsolutePath().normalize(), pointSlash.toAbsolutePath().normalize());
    }

    @Test
    void aPathWithDirectory_keepsItsDirectory() {
        assertEquals(Path.of("docs"), UMLGenerator.outputFolder(Path.of("docs", "archi.svg")));
    }
}
