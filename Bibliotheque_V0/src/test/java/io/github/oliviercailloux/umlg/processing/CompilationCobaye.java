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

// Compile un code source avec le processeur activé, et rend le verdict de javac.
//
// Ce n'est pas une classe de test : elle ne contient aucun @Test. Elle reprend le harnais des
// tests d'intégration existants — ToolProvider.getSystemJavaCompiler() —, en y ajoutant les deux
// pièces qui manquaient : un DiagnosticCollector pour relire les messages, et setProcessors pour
// installer le processeur.
//
// setProcessors plutôt que la découverte automatique : le test dit alors exactement ce qu'il
// mesure, sans dépendre de META-INF/services ni de la façon dont Maven a monté le classpath.
final class CompilationCobaye {

    // Le verdict : la compilation a-t-elle réussi, et qu'a dit javac.
    record Verdict(boolean reussie, List<String> erreurs, List<String> avertissements) {

        // Vrai si l'une des erreurs contient ce fragment. Les tests s'en servent pour vérifier
        // que le message explique la faute, sans figer sa formulation entière.
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

    // La variante à options : « -Aumlg.warnings=none » et rien d'autre, pour l'instant.
    static Verdict compiler(Path repertoire, String nomFichier, String code, List<String> options)
        throws IOException {
        return compilerTous(repertoire, Map.of(nomFichier, code), options);
    }

    // Plusieurs fichiers dans une seule tâche javac, et donc devant un seul processeur.
    //
    // La distinction compte pour les contrôles qui portent sur le projet et non sur un fichier —
    // deux paquetages qui déclareraient chacun @UMLDiagram. Compilés l'un après l'autre, chaque
    // appel construit un processeur neuf, dont l'état est vierge : la contradiction ne peut alors
    // pas être vue, et le test mesurerait le harnais plutôt que la bibliothèque.
    //
    // La clé est un chemin relatif, les répertoires de paquetage étant créés au passage : deux
    // package-info.java ne peuvent cohabiter qu'ainsi.
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
                // MANDATORY_WARNING autant que WARNING : la distinction est celle de javac, qui
                // réserve la première aux avertissements que -nowarn ne peut pas éteindre. Les
                // nôtres sont des WARNING, mais un test ne doit pas dépendre de ce choix.
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

    // Le classpath du test lui-même, plus le module path : selon la façon dont Maven lance les
    // tests, nos classes se trouvent sur l'un ou sur l'autre.
    private static String cheminDeCompilation() {
        return Stream.of(System.getProperty("java.class.path"),
                System.getProperty("jdk.module.path"))
            .filter(chemin -> chemin != null && !chemin.isBlank())
            .collect(Collectors.joining(File.pathSeparator));
    }
}
