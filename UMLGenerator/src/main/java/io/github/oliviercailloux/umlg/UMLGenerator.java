package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import io.github.oliviercailloux.umlg.extraction.ProjectScanner;
import io.github.oliviercailloux.umlg.extraction.ScanResult;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.rendering.PlantUmlWriter;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import io.github.oliviercailloux.umlg.warning.Warner;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * The facade: the library's only entry point.
 *
 * <pre>{@code
 * UMLGenerator.builder()
 *     .sourceDirectory("target/classes")
 *     .outputFile("architecture.svg")
 *     .build()
 *     .generate();
 * }</pre>
 */
public final class UMLGenerator {
    /** The name of the file listing the classes left out of the diagram. */
    public static final String CLASSES_IGNORED_FILE = "ignored-classes.txt";

    private final Path sourceDirectory;
    private final String outputFileName;
    private final Path outputDirectory;
    private final String extension;
    private final UmlStyle style;
    private final ImmutableList<Path> classpath;
    private final boolean keepPrevious;

    private UMLGenerator(Builder builder) {
        this.sourceDirectory = builder.sourceDirectory;
        this.outputFileName = builder.outputFileName;
        this.outputDirectory = builder.outputDirectory;
        this.extension = builder.extension;
        this.style = builder.style;
        this.classpath = builder.classpath;
        this.keepPrevious = builder.keepPrevious;
    }

    /** {@return how to describe the generation to run, setting by setting} */
    public static Builder builder() {
        return new Builder();
    }

    /** {@return the colour names an annotation may write, sorted} */
    public static ImmutableSortedSet<String> knownColors() {
        return ColorCodec.namesKnown();
    }

    /**
     * {@return what the generation did: files written, classes read, classes ignored, *
     * warnings}
     */
    public GenerationReport generate() {
        ImmutableList.Builder<String> warnings = ImmutableList.builder();
        try (Warnings.Channel capture =
            Warner.onTopOfTheCurrentChannel(warnings::add)) {
            return generated(warnings);
        }
    }

    private GenerationReport generated(ImmutableList.Builder<String> warnings) {
        try {
            ScanResult result = ProjectScanner.scan(sourceDirectory, classpath);
            UmlProject project = result.project();

            ImmutableList<Path> written;
            if (outputDirectory != null) {
                written = PlantUmlWriter.writeViews(project, outputDirectory, extension, style,
                    keepPrevious);
            } else {
                reportLostViews(project);
                PlantUmlWriter.write(project, outputFileName, style);
                written = ImmutableList.of(Paths.get(outputFileName));
            }

            ImmutableList<String> ignored = result.classesIgnored();
            Optional<Path> list = ignoredWriteList(ignored, written);

            return new GenerationReport(project.entities().size(), ignored, written, list,
                warnings.build());
        } catch (FileSystemException e) {
            throw new RuntimeException("Failed to generate the UML diagram: the output path "
                + e.getFile() + " cannot be written ("
                + (e.getReason() == null ? e.getClass().getSimpleName() : e.getReason())
                + "). Check the directory exists and that you have permission to write in it.", e);
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException(
                "Failed to generate the UML diagram (source directory: " + sourceDirectory
                    + ", output: " + (outputDirectory != null ? outputDirectory : outputFileName)
                    + "): " + e.getMessage(), e);
        }
    }

    private static void reportLostViews(UmlProject project) {
        int lost = project.views().size() - 1;
        if (lost > 0) {
            Warner.say("this project declares " + lost + " partial view(s), which a "
                + "single output file cannot hold: only the whole-project diagram is written. "
                + "Ask for a directory — .outputDirectory(\"diagrams\") — to get them all.");
        }
    }

    private static Optional<Path> ignoredWriteList(ImmutableList<String> ignored,
        ImmutableList<Path> written) throws IOException {
        Path file = outputFolder(written.get(0)).resolve(CLASSES_IGNORED_FILE);
        Files.deleteIfExists(file);

        if (ignored.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder content = new StringBuilder();
        content.append("Classes seen without @UMLClass, hence not drawn (")
               .append(ignored.size()).append("):\n");
        for (String clazz : ignored) {
            content.append(clazz).append('\n');
        }
        Files.writeString(file, content.toString());
        return Optional.of(file);
    }

    static Path outputFolder(Path written) {
        Path parent = written.getParent();
        return parent == null ? Paths.get("") : parent;
    }

    /**
     * Runs the generation from the command line.
     *
     * @param args the class directory, then the output file or directory, then the
     *     format.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: UMLGenerator <source-directory> "
                + "[output-file.svg|output-directory] [png|svg]");
            System.exit(1);

            return;
        }

        try {
            Builder builder = builder().sourceDirectory(args[0]);

            if (args.length >= 2) {
                if (PlantUmlWriter.supports(args[1])) {
                    builder.outputFile(args[1]);
                } else {
                    builder.outputDirectory(args[1]);
                }
            }
            if (args.length >= 3) {
                builder.outputFormat(args[2]);
            }
            System.out.println(builder.build().generate().summary());
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            System.err.println("Error: " + (cause.getMessage() == null
                ? e.getMessage() : cause.getMessage()));
            System.exit(1);
        }
    }

    /** How to describe the generation to run; only the source directory is mandatory. */
    public static final class Builder {
        private Path sourceDirectory;
        private String outputFileName = "diagram_architecture.svg";

        private Path outputDirectory;
        private String extension = ".svg";

        private boolean fileRequested;
        private boolean formatRequested;

        private UmlStyle style = UmlStyle.PLANTUML;

        private ImmutableList<Path> classpath = ImmutableList.of();

        private boolean keepPrevious;

        private Builder() {}

        /**
         * Names the code to read.
         *
         * @param path the directory of compiled classes to read. Mandatory.
         * @return this builder.
         */
        public Builder sourceDirectory(String path) {
            checkNotNull(path, "The source path cannot be null.");
            Path p = Paths.get(path);

            checkArgument(Files.exists(p) && Files.isDirectory(p),
                "The given source directory does not exist or is not a directory: %s", path);

            this.sourceDirectory = p;
            return this;
        }

        /**
         * Asks for a single file, carrying the whole diagram.
         *
         * @param fileName the single file to write, in .svg or .png.
         * @return this builder.
         */
        public Builder outputFile(String fileName) {
            checkNotNull(fileName, "The file name cannot be null.");

            checkArgument(PlantUmlWriter.supports(fileName),
                "The output format must be .png or .svg (got: %s)", fileName);

            this.outputFileName = fileName;
            this.fileRequested = true;
            return this;
        }

        /**
         * Asks for a directory, where each view gets its own image.
         *
         * @param path the directory where one image per view is written, rather than a single file.
         * @return this builder.
         */
        public Builder outputDirectory(String path) {
            checkNotNull(path, "The output directory cannot be null.");
            this.outputDirectory = Paths.get(path);
            return this;
        }

        /**
         * Decides the fate of the previous generation.
         *
         * @param keepPrevious true to file the previous generation in a neighbouring directory
         *     numbered rather than replaced.
         * @return this builder.
         */
        public Builder keepPrevious(boolean keepPrevious) {
            this.keepPrevious = keepPrevious;
            return this;
        }

        /**
         * Chooses the image format.
         *
         * @param extensionRequested png or svg; meaningful only with an output directory.
         * @return this builder.
         */
        public Builder outputFormat(String extensionRequested) {
            checkNotNull(extensionRequested, "The output format cannot be null.");
            String normalized = extensionRequested.startsWith(".")
                ? extensionRequested
                : "." + extensionRequested;
            checkArgument(PlantUmlWriter.supports("x" + normalized),
                "The output format must be .png or .svg (got: %s)", extensionRequested);

            this.extension = normalized;
            this.formatRequested = true;
            return this;
        }

        /**
         * Chooses who decides the colours.
         *
         * @param style who decides the colours, PlantUML or the library.
         * @return this builder.
         */
        public Builder style(UmlStyle style) {
            this.style = checkNotNull(style, "The style cannot be null. Use UmlStyle.PLANTUML.");
            return this;
        }

        /**
         * Names the archives the code read depends on.
         *
         * @param entries the archives the code read depends on, without which its classes do not load
         *     not.
         * @return this builder.
         */
        public Builder classpath(List<String> entries) {
            checkNotNull(entries, "The classpath cannot be null (use an empty list).");
            this.classpath = entries.stream()
                .map(Paths::get)
                .collect(ImmutableList.toImmutableList());
            return this;
        }

        /** {@return the described generator, ready to generate} */
        public UMLGenerator build() {
            checkNotNull(sourceDirectory,
                "The source directory is mandatory. Use .sourceDirectory(...)");
            checkState(!(fileRequested && outputDirectory != null),
                "Both an output file (%s) and an output directory (%s) are asked for, and only "
                    + "one of them can be written. Keep .outputFile(...) for the single "
                    + "whole-project diagram, or .outputDirectory(...) to get every view.",
                outputFileName, outputDirectory);
            checkState(!(formatRequested && outputDirectory == null),
                "An output format (%s) is asked for, but no output directory: in single-file mode "
                    + "the format is read from the file name. Write .outputFile(\"diagram%s\"), or "
                    + "ask for a directory with .outputDirectory(...).",
                extension, extension);

            checkState(!(keepPrevious && outputDirectory == null),
                ".keepPrevious(true) asks for the previous generation to be kept, but there is no "
                    + "output directory: in single-file mode the file is simply overwritten, and "
                    + "there is no neighbouring folder to number. Ask for a directory with "
                    + ".outputDirectory(...), or drop .keepPrevious(true).");
            return new UMLGenerator(this);
        }
    }
}
