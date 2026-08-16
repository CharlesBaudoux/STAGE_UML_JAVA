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

// The javac harness of the tests starting from real compiled classes. This is not a test
// test: it holds no @Test.
//
// The procedure is the same everywhere — write a .java in a temporary directory, compile it for
// sound, then scan the output directory. It is the only way of exercising what exists
// than at that level: the path → qualified name translation, nested packages, unreadable
// unreadable, a parent living in a jar. It was copied into four files, with as many
// variants as there were copies — one joined the module path to the classpath, the other did not;
// one raised on failure, the other asserted. It lives here once.
//
// WHY -proc:none
//
// The test classpath holds our own classes, hence the META-INF/services declaring
// UmlgProcessor: without this option, javac would install it and would refuse the deliberately
// faulty. Yet those tests measure the "scan then render" chain, and with it the validation
// at run time — the second line of defence, the one protecting the user compiling without the
// the processor, and the one building the model programmatically. The processor is the business of
// CompilationFixture and of the tests of the processing package.
//
// The class is public because its callers live in three packages.
public final class CompilationWithoutProcessor {

    private CompilationWithoutProcessor() {}

    /**
     * Writes the given sources, then compiles them all in a single javac task.
     *
     * <p>A single task, and not one per file: two fixtures naming each other would
     * would not compile otherwise. The package directories are created along the way.
     *
     * @param rootSources the root under which the {@code .java} files are written
     * @param output the directory where the {@code .class} files are dropped — the one the scan will read
     * @param pathsAndCodes the arguments go in pairs: a relative path, then the code
     * @throws IOException if writing the sources fails
     */
    public static void compiler(Path rootSources, Path output, String... pathsAndCodes)
        throws IOException {
        if (pathsAndCodes.length % 2 != 0) {
            throw new IllegalArgumentException(
                "The arguments go in pairs: a path, then the code.");
        }

        List<File> files = new ArrayList<>();
        for (int i = 0; i < pathsAndCodes.length; i += 2) {
            Path file = rootSources.resolve(pathsAndCodes[i]);
            // The package directories do not exist yet: to be created before writing.
            Files.createDirectories(file.getParent());
            Files.writeString(file, pathsAndCodes[i + 1], StandardCharsets.UTF_8);
            files.add(file.toFile());
        }

        // The JDK compiler, reachable from the program itself. Absent from a plain JRE,
        // hence the check that follows.
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Ces tests exigent un JDK (javac absent).");
        }
        Files.createDirectories(output);

        try (StandardJavaFileManager manager =
            compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {

            // -classpath: the fixture imports our annotations, so they must be given back to it
            // visible. -d: where to drop the .class files.
            boolean ok = compiler.getTask(null, manager, null,
                List.of("-proc:none", "-classpath", compilationPath(), "-d",
                    output.toString()),
                null, manager.getJavaFileObjectsFromFiles(files)).call();

            if (!ok) {
                throw new IllegalStateException("The fixture did not compile:\n"
                    + String.join("\n", pathsAndCodes));
            }
        }
    }

    /**
     * The classpath of the test itself, plus the module path.
     *
     * <p>Depending on how Maven launches the tests, our classes are on one or on
     * the other. Both are concatenated rather than guessed at.
     *
     * @return what to pass to {@code -classpath}
     */
    public static String compilationPath() {
        return Stream.of(System.getProperty("java.class.path"),
                System.getProperty("jdk.module.path"))
            .filter(path -> path != null && !path.isBlank())
            .collect(Collectors.joining(File.pathSeparator));
    }
}
