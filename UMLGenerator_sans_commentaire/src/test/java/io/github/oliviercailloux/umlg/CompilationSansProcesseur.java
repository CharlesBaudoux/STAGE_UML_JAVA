package io.github.oliviercailloux.umlg;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class CompilationSansProcesseur {

    private CompilationSansProcesseur() {}

    /**
     * Écrit les sources données, puis les compile toutes dans une seule tâche javac.
     *
     * <p>Une seule tâche, et non une par fichier : deux cobayes qui se nomment l'un l'autre ne
     * compileraient pas autrement. Les répertoires de paquetage sont créés au passage.
     *
     * @param racineSources la racine sous laquelle écrire les {@code .java}
     * @param sortie le répertoire où déposer les {@code .class} — c'est lui que le scan lira
     * @param cheminsEtCodes les arguments vont par deux : un chemin relatif, puis le code
     * @throws IOException si l'écriture des sources échoue
     */
    public static void compiler(Path racineSources, Path sortie, String... cheminsEtCodes)
        throws IOException {
        if (cheminsEtCodes.length % 2 != 0) {
            throw new IllegalArgumentException(
                "Les arguments vont par deux : un chemin, puis le code.");
        }

        List<File> fichiers = new ArrayList<>();
        for (int i = 0; i < cheminsEtCodes.length; i += 2) {
            Path fichier = racineSources.resolve(cheminsEtCodes[i]);
            Files.createDirectories(fichier.getParent());
            Files.writeString(fichier, cheminsEtCodes[i + 1], StandardCharsets.UTF_8);
            fichiers.add(fichier.toFile());
        }

        JavaCompiler compilateur = ToolProvider.getSystemJavaCompiler();
        if (compilateur == null) {
            throw new IllegalStateException("Ces tests exigent un JDK (javac absent).");
        }
        Files.createDirectories(sortie);

        try (StandardJavaFileManager gestionnaire =
            compilateur.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {

            boolean ok = compilateur.getTask(null, gestionnaire, null,
                List.of("-proc:none", "-classpath", cheminDeCompilation(), "-d",
                    sortie.toString()),
                null, gestionnaire.getJavaFileObjectsFromFiles(fichiers)).call();

            if (!ok) {
                throw new IllegalStateException("Le cobaye n'a pas compilé :\n"
                    + String.join("\n", cheminsEtCodes));
            }
        }
    }

    /**
     * Le classpath du test lui-même, plus le module path.
     *
     * <p>Selon la façon dont Maven lance les tests, nos classes se trouvent sur l'un ou sur
     * l'autre. On concatène les deux plutôt que de deviner.
     *
     * @return de quoi passer à {@code -classpath}
     */
    public static String cheminDeCompilation() {
        return Stream.of(System.getProperty("java.class.path"),
                System.getProperty("jdk.module.path"))
            .filter(chemin -> chemin != null && !chemin.isBlank())
            .collect(Collectors.joining(File.pathSeparator));
    }
}
