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

// Le harnais javac des tests qui partent de vraies classes compilées. Ce n'est pas une classe de
// test : elle ne contient aucun @Test.
//
// Le procédé est le même partout — écrire un .java dans un répertoire temporaire, le compiler pour
// de bon, puis scanner le répertoire de sortie. C'est le seul moyen d'éprouver ce qui n'existe
// qu'à ce niveau : la traduction chemin → nom qualifié, les paquetages imbriqués, les fichiers
// illisibles, un parent qui vit dans un jar. Il était recopié dans quatre fichiers, avec autant de
// variantes qu'il y avait de copies — l'une joignait le module path au classpath, l'autre non ;
// l'une levait sur échec, l'autre assertait. Il vit ici une fois.
//
// POURQUOI -proc:none
//
// Le classpath des tests contient nos propres classes, donc le META-INF/services qui déclare
// UmlgProcessor : sans cette option, javac l'installerait et refuserait les cobayes délibérément
// fautifs. Or ces tests-là mesurent la chaîne « scan puis rendu », et avec elle la validation
// d'exécution — la seconde ligne de défense, celle qui protège l'utilisateur compilant sans le
// processeur, et celui qui construit le modèle par programme. Le processeur, lui, est l'affaire de
// CompilationCobaye et des tests du paquetage processing.
//
// La classe est publique parce que les appelants vivent dans trois paquetages.
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
            // Les répertoires de paquetage n'existent pas encore : à créer avant d'écrire.
            Files.createDirectories(fichier.getParent());
            Files.writeString(fichier, cheminsEtCodes[i + 1], StandardCharsets.UTF_8);
            fichiers.add(fichier.toFile());
        }

        // Le compilateur du JDK, accessible depuis le programme lui-même. Absent d'un simple JRE,
        // d'où la vérification qui suit.
        JavaCompiler compilateur = ToolProvider.getSystemJavaCompiler();
        if (compilateur == null) {
            throw new IllegalStateException("Ces tests exigent un JDK (javac absent).");
        }
        Files.createDirectories(sortie);

        try (StandardJavaFileManager gestionnaire =
            compilateur.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {

            // -classpath : le cobaye importe nos annotations, il faut donc les lui rendre
            // visibles. -d : où déposer les .class.
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
