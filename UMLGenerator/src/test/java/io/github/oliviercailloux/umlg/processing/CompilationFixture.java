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

final class CompilationFixture {

    static final String Header = """
        import io.github.oliviercailloux.umlg.annotation.*;
        import java.util.*;
        """;

    record Verdict(boolean successful, List<String> errors, List<String> warnings) {

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

    static Verdict compiler(Path directory, String nameFile, String code, List<String> options)
        throws IOException {
        return compileAll(directory, Map.of(nameFile, code), options);
    }

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

    private static String compilationPath() {
        return CompilationWithoutProcessor.compilationPath();
    }
}
