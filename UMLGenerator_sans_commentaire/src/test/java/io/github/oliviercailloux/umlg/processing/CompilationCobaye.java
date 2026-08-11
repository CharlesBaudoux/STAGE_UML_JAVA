package io.github.oliviercailloux.umlg.processing;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

final class CompilationCobaye {

    record Verdict(boolean reussie, List<String> erreurs, List<String> avertissements) {

        boolean erreurContient(String fragment) {
            return erreurs.stream().anyMatch(message -> message.contains(fragment));
        }

        boolean avertissementContient(String fragment) {
            return avertissements.stream().anyMatch(message -> message.contains(fragment));
        }

        String rapport() {
            return (erreurs.isEmpty() ? "(aucune erreur)" : String.join("\n", erreurs))
                + (avertissements.isEmpty() ? ""
                    : "\n-- avertissements --\n" + String.join("\n", avertissements));
        }
    }

    private CompilationCobaye() {}

    static Verdict compiler(Path repertoire, String nomFichier, String code) throws IOException {
        return compiler(repertoire, nomFichier, code, List.of());
    }

    static Verdict compiler(Path repertoire, String nomFichier, String code, List<String> options)
        throws IOException {
        return compilerTous(repertoire, Map.of(nomFichier, code), options);
    }

    static Verdict compilerTous(Path repertoire, Map<String, String> sources) throws IOException {
        return compilerTous(repertoire, sources, List.of());
    }

    private static Verdict compilerTous(Path repertoire, Map<String, String> sources,
        List<String> options) throws IOException {
        List<File> fichiers = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path fichier = repertoire.resolve(source.getKey());
            Files.createDirectories(fichier.getParent());
            Files.writeString(fichier, source.getValue(), StandardCharsets.UTF_8);
            fichiers.add(fichier.toFile());
        }

        JavaCompiler compilateur = ToolProvider.getSystemJavaCompiler();
        if (compilateur == null) {
            throw new IllegalStateException("Ces tests exigent un JDK (javac absent).");
        }
        Path sortie = repertoire.resolve("classes");
        Files.createDirectories(sortie);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager gestionnaire =
            compilateur.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {

            List<String> arguments = new ArrayList<>(
                List.of("-classpath", cheminDeCompilation(), "-d", sortie.toString()));
            arguments.addAll(options);

            JavaCompiler.CompilationTask tache = compilateur.getTask(null, gestionnaire,
                diagnostics, arguments, null,
                gestionnaire.getJavaFileObjectsFromFiles(fichiers));

            tache.setProcessors(List.of(new UmlgProcessor()));

            boolean reussie = tache.call();

            return new Verdict(reussie,
                messages(diagnostics, Diagnostic.Kind.ERROR),
                messages(diagnostics, Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING));
        }
    }

    private static List<String> messages(DiagnosticCollector<JavaFileObject> diagnostics,
        Diagnostic.Kind... gravites) {
        Set<Diagnostic.Kind> retenues = Set.of(gravites);
        return diagnostics.getDiagnostics().stream()
            .filter(d -> retenues.contains(d.getKind()))
            .map(d -> d.getMessage(null))
            .toList();
    }

    private static String cheminDeCompilation() {
        return Stream.of(System.getProperty("java.class.path"),
                System.getProperty("jdk.module.path"))
            .filter(chemin -> chemin != null && !chemin.isBlank())
            .collect(Collectors.joining(File.pathSeparator));
    }
}
