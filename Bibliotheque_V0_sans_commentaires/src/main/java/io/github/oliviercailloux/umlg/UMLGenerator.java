package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.extraction.ProjectScanner;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.rendering.PlantUmlWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// La façade : le seul point d'entrée de la bibliothèque.
//
// Usage typique :
//   UMLGenerator.builder()
//       .sourceDirectory("target/classes")
//       .outputFile("architecture.svg")
//       .build()
//       .generate();
//
// Elle ne fait qu'enchaîner les deux moitiés du travail : ProjectScanner lit le code annoté et
// en tire un modèle, PlantUmlWriter transforme ce modèle en image.
public final class UMLGenerator {

    private final Path sourceDirectory;
    private final String outputFileName;
    private final UmlStyle style;

    private UMLGenerator(Builder builder) {
        this.sourceDirectory = builder.sourceDirectory;
        this.outputFileName = builder.outputFileName;
        this.style = builder.style;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void generate() {
        try {
            UmlProject project = ProjectScanner.scan(sourceDirectory);
            PlantUmlWriter.write(project, outputFileName, style);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to generate the UML diagram (source directory: " + sourceDirectory
                    + ", output file: " + outputFileName + "): " + e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: UMLGenerator <source-directory> [output-file]");
            System.exit(1);
            return;
        }

        Builder builder = builder().sourceDirectory(args[0]);
        if (args.length >= 2) {
            builder.outputFile(args[1]);
        }
        builder.build().generate();
    }

    public static final class Builder {
        private Path sourceDirectory;
        private String outputFileName = "diagramme_architecture.svg";
        private UmlStyle style = UmlStyle.PLANTUML;

        private Builder() {}

        public Builder sourceDirectory(String path) {
            checkNotNull(path, "The source path cannot be null.");
            Path p = Paths.get(path);
            checkArgument(Files.exists(p) && Files.isDirectory(p),
                "The given source directory does not exist or is not a directory: %s", path);

            this.sourceDirectory = p;
            return this;
        }

        public Builder outputFile(String fileName) {
            checkNotNull(fileName, "The file name cannot be null.");
            checkArgument(PlantUmlWriter.supporte(fileName),
                "The output format must be .png or .svg (got: %s)", fileName);

            this.outputFileName = fileName;
            return this;
        }

        public Builder style(UmlStyle style) {
            this.style = checkNotNull(style, "The style cannot be null. Use UmlStyle.PLANTUML.");
            return this;
        }

        public UMLGenerator build() {
            checkNotNull(sourceDirectory,
                "The source directory is mandatory. Use .sourceDirectory(...)");
            return new UMLGenerator(this);
        }
    }
}
