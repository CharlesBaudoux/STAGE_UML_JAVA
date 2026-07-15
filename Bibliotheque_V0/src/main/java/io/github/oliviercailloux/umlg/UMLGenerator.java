package io.github.oliviercailloux.umlg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.extraction.ProjectScanner;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.rendering.PlantUmlWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Façade publique de la bibliothèque : point d'entrée unique pour scanner un
 * projet Java annoté et produire un diagramme PlantUML.
 */
public final class UMLGenerator {

    private final Path sourceDirectory;
    private final String outputFileName;

    private UMLGenerator(Builder builder) {
        this.sourceDirectory = builder.sourceDirectory;
        this.outputFileName = builder.outputFileName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void generate() {
        try {
            UmlProject project = ProjectScanner.scan(sourceDirectory);
            PlantUmlWriter.write(project, outputFileName);
        } catch (Exception e) {
            throw new RuntimeException(
                "Erreur lors de la génération du diagramme UML (dossier source : " + sourceDirectory
                    + ", fichier de sortie : " + outputFileName + ") : " + e.getMessage(), e);
        }
    }

    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage : UMLGenerator <dossier-source> [fichier-sortie]");
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
        private String outputFileName = "diagramme_architecture.svg"; // Smart Default

        private Builder() {}

        public Builder sourceDirectory(String path) {
            checkNotNull(path, "Le chemin source ne peut pas être null.");
            Path p = Paths.get(path);
            checkArgument(Files.exists(p) && Files.isDirectory(p),
                "Le dossier source spécifié n'existe pas ou n'est pas un dossier : %s", path);

            this.sourceDirectory = p;
            return this;
        }

        public Builder outputFile(String fileName) {
            checkNotNull(fileName, "Le nom du fichier ne peut pas être null.");
            checkArgument(PlantUmlWriter.supporte(fileName),
                "Le format de sortie doit être .png ou .svg (reçu : %s)", fileName);

            this.outputFileName = fileName;
            return this;
        }

        public UMLGenerator build() {
            checkNotNull(sourceDirectory, "Le dossier source est obligatoire. Utilisez .sourceDirectory(...)");
            return new UMLGenerator(this);
        }
    }
}
