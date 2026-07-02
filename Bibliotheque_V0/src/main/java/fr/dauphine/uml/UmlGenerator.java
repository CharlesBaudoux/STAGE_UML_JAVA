package fr.dauphine.uml;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Point d'entrée principal de la bibliothèque de génération UML.
 */
public final class UmlGenerator {

    private final Path sourceDirectory;
    private final String outputFileName;

    // Constructeur privé : l'utilisateur DOIT passer par le Builder
    private UmlGenerator(Builder builder) {
        this.sourceDirectory = builder.sourceDirectory;
        this.outputFileName = builder.outputFileName;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Déclenche l'analyse statique et la création du fichier image.
     */
    public void generate() {
        try {
            // La complexité est déléguée aux classes internes "package-private"
            UmlProject project = ProjectScanner.scan(sourceDirectory);
            PlantUmlWriter.write(project, outputFileName);
        } catch (Exception e) {
            throw new RuntimeException("Erreur critique lors de la génération de l'UML", e);
        }
    }

    // --- Le Builder (Interface Fluide) ---
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
            checkArgument(fileName.endsWith(".png") || fileName.endsWith(".svg"), 
                "Le format de sortie doit être .png ou .svg");
            
            this.outputFileName = fileName;
            return this;
        }

        public UmlGenerator build() {
            checkNotNull(sourceDirectory, "Le dossier source est obligatoire. Utilisez .sourceDirectory(...)");
            return new UmlGenerator(this);
        }
    }
}