package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.CompilationWithoutProcessor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

// Compiles a source with the processor enabled, and returns javac's verdict.
//
// This is not a test class: it holds no @Test. It reuses the harness of the
// existing integration tests — ToolProvider.getSystemJavaCompiler() —, adding the two
// missing pieces: a DiagnosticCollector to read the messages back, and setProcessors to
// installing the processor.
//
// setProcessors rather than automatic discovery: the test then says exactly what it
// measured, without depending on META-INF/services nor on how Maven set up the classpath.
final class CompilationFixture {

    // The header every fixture of the package shares: the annotations, and the types of the
    // standard library the bodies use. It was copied at the head of seven files,
    // with as many variants as copies — one imported List, another List and Map, another
    // nothing.
    //
    // Imports on demand rather than named, and every fixture receives them: an unused
    // unused costs nothing — javac says not a word about it, and the tests requiring silence from the
    // the processor are not bothered by it — whereas a missing import fails the fixture
    // on a fault other than the one being measured.
    static final String Header = """
        import io.github.oliviercailloux.umlg.annotation.*;
        import java.util.*;
        """;

    // The verdict: did the compilation succeed, and what did javac say.
    record Verdict(boolean successful, List<String> errors, List<String> warnings) {

        // True if one of the errors holds this fragment. The tests use it to check
        // that the message explains the fault, without freezing its whole wording.
        boolean errorContains(String fragment) {
            return errors.stream().anyMatch(message -> message.contains(fragment));
        }

        boolean warningContains(String fragment) {
            return warnings.stream().anyMatch(message -> message.contains(fragment));
        }

        String report() {
            return (errors.isEmpty() ? "(no error)" : String.join("\n", errors))
                + (warnings.isEmpty() ? ""
                    : "\n-- warnings --\n" + String.join("\n", warnings));
        }
    }

    private CompilationFixture() {}

    // The ordinary form: a fixture body, preceded by the shared header, in "Fixture.java".
    // Almost every test of the package goes through it.
    static Verdict compileBody(Path directory, String body) throws IOException {
        return compileBody(directory, body, List.of());
    }

    static Verdict compileBody(Path directory, String body, List<String> options)
        throws IOException {
        return compiler(directory, "Fixture.java", Header + body, options);
    }

    static Verdict compiler(Path directory, String nameFile, String code) throws IOException {
        return compiler(directory, nameFile, code, List.of());
    }

    // The option variant: "-Aumlg.warnings=none" and nothing else, for now.
    static Verdict compiler(Path directory, String nameFile, String code, List<String> options)
        throws IOException {
        return compileAll(directory, Map.of(nameFile, code), options);
    }

    // Several files in a single javac task, and hence before a single processor.
    //
    // The distinction matters for the checks bearing on the project and not on a file —
    // two classes contradicting each other at a distance. Compiled one after the other, each
    // call builds a fresh processor, whose state is blank: the contradiction can then
    // not be seen, and the test would measure the harness rather than the library.
    //
    // The key is a relative path, the package directories being created along the way: two
    // package-info.java can coexist only that way.
    static Verdict compileAll(Path directory, Map<String, String> sources) throws IOException {
        return compileAll(directory, sources, List.of());
    }

    private static Verdict compileAll(Path directory, Map<String, String> sources,
        List<String> options) throws IOException {
        List<File> files = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = directory.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
            files.add(file.toFile());
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Ces tests exigent un JDK (javac absent).");
        }
        Path output = directory.resolve("classes");
        Files.createDirectories(output);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager manager =
            compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {

            List<String> arguments = new ArrayList<>(
                List.of("-classpath", compilationPath(), "-d", output.toString()));
            arguments.addAll(options);

            JavaCompiler.CompilationTask task = compiler.getTask(null, manager,
                diagnostics, arguments, null,
                manager.getJavaFileObjectsFromFiles(files));

            task.setProcessors(List.of(new UmlgProcessor()));

            boolean successful = task.call();

            return new Verdict(successful,
                messages(diagnostics, Diagnostic.Kind.ERROR),
                // MANDATORY_WARNING as much as WARNING: the distinction is javac's, which
                // reserves the first for the warnings -nowarn cannot silence. Ours
                // ours are WARNINGs, but a test must not depend on that choice.
                messages(diagnostics, Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING));
        }
    }

    private static List<String> messages(DiagnosticCollector<JavaFileObject> diagnostics,
        Diagnostic.Kind... severities) {
        Set<Diagnostic.Kind> kept = Set.of(severities);
        return diagnostics.getDiagnostics().stream()
            .filter(d -> kept.contains(d.getKind()))
            .map(d -> d.getMessage(null))
            .toList();
    }

    // The classpath, computed by CompilationWithoutProcessor: both harnesses compile the same
    // kind of fixture, and differ only by the processor.
    private static String compilationPath() {
        return CompilationWithoutProcessor.compilationPath();
    }
}
