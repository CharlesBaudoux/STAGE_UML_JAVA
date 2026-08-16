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

// The facade seen from outside: what the builder accepts, what it refuses, and what a
// the generation really leaves on disk.
//
// Each refusal is checked at the right call: the invalid directory fails as early as sourceDirectory(),
// the invalid extension as early as outputFile(), the missing source directory and the inconsistencies between
// two settings only at build(). That is no detail: it is what gives a message
// pointing at the faulty line.
//
// WHY THIS FILE NO LONGER SCANS target/test-classes
//
// It did so four times, and alone weighed a third of the suite's time to check
// that a facade really passes its parameters on. The hundreds of fixtures of the other tests
// proved nothing more here than a single annotated class, copied into a directory of its own. This
// which target/test-classes protects — that no fixture added tomorrow breaks the rendering of
// the whole — is the business of UMLGeneratorIntegrationTest, which does it once and for all.
class UMLGeneratorTest {

    // The marker of the library theme in the generated source: it appears there only under
    // UmlStyle.UMLG. That is what makes it possible to check that the requested style is really applied, and
    // not merely accepted.
    private static final String THEME_UMLG_MARKER = "skinparam ClassBorderColor #33526E";

    // The only class the generations of this file have to read.
    @UMLClass(name = "FacadeFixture", strategy = UMLVisibility.PUBLIC)
    static class FacadeFixture {
        public String name;
    }

    // A directory of compiled classes holding only the fixture above: its .class is
    // copied from target/test-classes, in its place in the package tree. The scan
    // finds one class and one only, which makes each generation of this file almost
    // instantaneous.
    private static Path aDirectoryOfOneClass(Path tmp) throws IOException {
        String path = FacadeFixture.class.getName().replace('.', '/') + ".class";
        Path root = tmp.resolve("classes");
        Path destination = root.resolve(path);
        Files.createDirectories(destination.getParent());
        Files.copy(Path.of("target", "test-classes").resolve(path), destination);
        return root;
    }

    // The builder of a generation over this directory.
    private static UMLGenerator.Builder onFixture(Path tmp) throws IOException {
        return UMLGenerator.builder().sourceDirectory(aDirectoryOfOneClass(tmp).toString());
    }

    // --- What the builder refuses, and when ---

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
        // The style has a defect, but "null" is not one: it would be an oversight, and leaving it
        // letting it through would fail the rendering much later, without saying why.
        assertThrows(NullPointerException.class,
            () -> UMLGenerator.builder().sourceDirectory(tmp.toString()).style(null));
    }

    // --- The settings that silently contradicted each other ---

    @Test
    void anOutputFileAndDirectory_areRefusedTogether(@TempDir Path tmp) {
        // generate() decided by "if (outputDirectory != null)": the named file was
        // never written, and nothing said so. The user looked for "archi.png" where it
        // had asked for it, and did not find it there.
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
        // outputFormat is read only in the directory branch: ".outputFile(\"a.png\")" followed by
        // ".outputFormat(\"svg\")" delivered PNG to someone who had just asked for SVG.
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
        // The third setting that silently contradicted itself: keepPrevious is read only in the
        // directory branch, so that in single-file mode the previous generation was
        // overwritten by the very one asking to keep it.
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
        // keepPrevious(false) asks for what single-file mode already does: it is not a
        // contradiction, and the check must not close on it.
        assertDoesNotThrow(() -> UMLGenerator.builder()
            .sourceDirectory(tmp.toString())
            .outputFile("archi.svg")
            .keepPrevious(false)
            .build());
    }

    @Test
    void aFormatRequestedWithADirectory_isApplied(@TempDir Path tmp) throws IOException {
        // The check must not close on the legitimate use, which is the Maven plugin's:
        // a directory, and the format of the images dropped in it.
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
        // outputFileName carries a default — "diagram_architecture.svg" — and two defaults do not
        // do not contradict each other. Only an explicit request conflicts with another, for lack of
        // which would have made directory mode unusable.
        Path folder = tmp.resolve("diagrams");

        GenerationReport report = onFixture(tmp)
            .outputDirectory(folder.toString())
            .build()
            .generate();

        assertEquals(List.of(folder.resolve("project.svg")), report.files());
    }

    @Test
    void viewsInSingleFileMode_areReported(@TempDir Path tmp) throws IOException {
        // A project declaring views and writing a single file loses them all, without a word.
        // Nothing, in ".outputFile(\"archi.svg\")", says what the rules file asked for
        // from elsewhere: the warning is the only place where the two meet.
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
        // The counterpart of the previous test: in directory mode, they are all written, and there is
        // nothing to report.
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

    // --- What a generation really leaves behind ---

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
        // The existing test checked only that the parameter was accepted: passing UmlStyle.UMLG
        // to the builder proved nothing about what is drawn. The style shows only in the
        // the generated document, and that is where it must be read.
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

    // --- keepPrevious, from the builder to the disk ---

    @Test
    void keepPrevious_writesTheNextGenerationAlongside(@TempDir Path tmp) throws IOException {
        // The behaviour was tested one floor below, but its builder → generate() wiring was
        // was not: flipping the boolean along the way would have failed no test.
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

    // --- classpath: the API the Maven plugin uses ---

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

        // The class could not be linked: it does not enter the model, and the project
        // finds itself with no box. That is the exact scenario the classpath repairs.
        assertTrue(said.stream().anyMatch(a -> a.contains("appli.Child")),
            "The loading failure must be said, and name the class: " + said);
        assertTrue(e.getMessage().contains("nothing to draw"), e.getMessage());
    }

    @Test
    void withTheClasspath_aClassWhoseParentLivesInAJar_isDrawn(@TempDir Path tmp)
        throws Exception {
        // It is the API the Maven plugin uses, and that nobody exercised: it runs in the
        // Maven's JVM, and without it every class inheriting from a third-party library would vanish
        // of the diagram of real projects — the ones whose diagram is most useful.
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

    // A project with one annotated class whose parent is elsewhere: compiled in one go, then the
    // the parent is moved into a jar set alongside. The analysed directory therefore holds only
    // the child, exactly like the target/classes of a project depending on a library.
    private static Path aProjectWhoseParentIsInAJar(Path tmp) throws Exception {
        Path classes = tmp.resolve("compilees");
        // Both in a single javac task: Child names Base, and one would not compile
        // without l'other.
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

    // --- The command line ---

    @Test
    void commandLine_withANameWithoutExtension_writesAViewDirectory(@TempDir Path tmp)
        throws Exception {
        // The gap this test guards: outputDirectory existed in the builder, and the command
        // command line gave no access to it — the main feature was unreachable for
        // which did not write its own launch class.
        Path output = tmp.resolve("diagrams");

        UMLGenerator.main(
            new String[] {aDirectoryOfOneClass(tmp).toString(), output.toString()});

        assertTrue(Files.isDirectory(output), "One name without extension requested un folder.");
        assertTrue(Files.exists(output.resolve("project.svg")), "The full view is written there.");
    }

    @Test
    void commandLine_withAnExtension_writesThatSingleFile(@TempDir Path tmp) throws Exception {
        // Nobody calls a directory "architecture.svg": the extension is enough to say what
        // that one wants, and avoids one more option.
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

    // WHERE TO DROP THE LIST OF IGNORED CLASSES
    //
    // A bare file name has no parent, and that is not the same as having no
    // destination: it is the current directory. Confusing the two deprived it of its list
    // the default invocation — "umlg target/classes" — as well as any output named without
    // directory, whereas the same call prefixed with "./" got it. Two commands that
    // drop the file in the same place must behave alike.

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
