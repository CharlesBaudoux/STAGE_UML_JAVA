package io.github.oliviercailloux.umlg.maven;

import io.github.oliviercailloux.umlg.Avertissements;
import io.github.oliviercailloux.umlg.RapportDeGeneration;
import io.github.oliviercailloux.umlg.UMLGenerator;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

// « mvn umlg:generate » : les diagrammes du projet, sans un argument à écrire.
//
// CE QUE CE MOJO REMPLACE
//
// Avant lui, chaque projet invoquait exec-maven-plugin avec une mainClass et deux arguments
// positionnels — répertoire analysé, dossier de sortie — dont rien ne disait le sens ni l'ordre.
// Un projet réel en avait deux déclarations concurrentes, et Maven s'en plaignait. Ici, les
// défauts disent l'usage : le répertoire analysé est target/classes, la sortie est diagrammes/ à
// la racine du projet, et chaque réglage se surcharge par configuration ou par -Dumlg.*.
//
// POURQUOI LE CLASSPATH DU PROJET EST TRANSMIS
//
// La bibliothèque lit les classes compilées par réflexion, et la réflexion ne s'arrête pas au
// répertoire : charger une classe résout son parent et ses interfaces, lire un attribut résout
// son type — qui vivent dans les dépendances du projet. exec:java les apportait d'office, en
// exécutant dans le classpath du projet ; ce Mojo tourne dans la JVM de Maven et doit les
// donner explicitement (requiresDependencyResolution = RUNTIME, puis Builder.classpath).
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class GenerateMojo extends AbstractMojo {

    // Le répertoire des classes compilées — pas des sources : la bibliothèque travaille par
    // réflexion, donc sur du code déjà compilé.
    @Parameter(defaultValue = "${project.build.outputDirectory}",
        property = "umlg.sourceDirectory")
    private String sourceDirectory;

    // Le dossier des diagrammes : la vue intégrale à sa racine, les autres dans un sous-dossier,
    // et le fichier umlg.rules du projet — recopié dans target/classes par la construction — y
    // est lu. À la racine du projet plutôt que dans target/ : les diagrammes sont une
    // documentation, faite pour être versionnée, pas un sous-produit que clean efface.
    @Parameter(defaultValue = "${project.basedir}/diagrammes", property = "umlg.outputDirectory")
    private String outputDirectory;

    // Un seul fichier plutôt qu'un dossier : « -Dumlg.outputFile=architecture.svg » écrit la
    // seule vue d'ensemble, la forme d'origine de la bibliothèque. Quand il est donné, le
    // dossier n'est pas écrit.
    @Parameter(property = "umlg.outputFile")
    private String outputFile;

    // Le format des images du dossier : « svg » (défaut) ou « png ».
    @Parameter(defaultValue = "svg", property = "umlg.format")
    private String format;

    // « -Dumlg.skip=true » : la génération est sautée, la construction continue.
    @Parameter(defaultValue = "false", property = "umlg.skip")
    private boolean skip;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("umlg generation skipped (umlg.skip = true).");
            return;
        }
        // Le diagnostic le plus fréquent, dit avant que le Builder ne le dise moins bien : le
        // goal a été lancé avant la compilation, il n'y a rien à analyser.
        if (!Files.isDirectory(Paths.get(sourceDirectory))) {
            throw new MojoExecutionException("The directory to analyse does not exist: "
                + sourceDirectory + ". Compile first — \"mvn compile umlg:generate\" — or point "
                + "umlg.sourceDirectory at the compiled classes.");
        }

        List<String> classpath;
        try {
            classpath = project.getRuntimeClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            // Inatteignable : requiresDependencyResolution = RUNTIME les a fait résoudre.
            throw new MojoExecutionException("The runtime classpath could not be resolved.", e);
        }

        // Les avertissements de la génération passent par le log de Maven, avec le préfixe
        // [WARNING] et la place que Maven leur réserve dans son récapitulatif de fin. Ils
        // partaient jusqu'ici sur la sortie d'erreur, nus, au milieu du log — le message le plus
        // utile de l'outil était celui qu'on voyait le moins.
        Avertissements.versLeCanal(getLog()::warn);
        try {
            // La construction du builder est dans le try, et ce n'est pas un détail de style :
            // hors de lui, une faute de configuration — « -Dumlg.format=pdf » — s'échappait en
            // IllegalArgumentException nue, et Maven la présentait en PluginExecutionException,
            // c'est-à-dire comme un défaut du plugin plutôt que comme la faute de frappe qu'elle
            // est.
            UMLGenerator.Builder builder = UMLGenerator.builder()
                .sourceDirectory(sourceDirectory)
                .classpath(classpath);

            if (outputFile != null && !outputFile.isBlank()) {
                // Résolu depuis la racine du module, comme outputDirectory l'est déjà par son
                // défaut. Passé tel quel, un chemin relatif s'entendait depuis le répertoire d'où
                // Maven a été lancé : dans un réacteur, tous les modules écrivaient alors le même
                // fichier à la racine, chacun écrasant le précédent.
                builder.outputFile(project.getBasedir().toPath().resolve(outputFile).toString());
            } else {
                builder.outputDirectory(outputDirectory).outputFormat(format);
            }

            RapportDeGeneration rapport = builder.build().generate();
            // La bibliothèque rend son compte rendu sans l'écrire nulle part : c'est ici qu'il
            // trouve son canal, celui où l'utilisateur d'un build le cherche.
            for (String ligne : rapport.resume().split("\n")) {
                getLog().info(ligne);
            }
        } catch (RuntimeException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            // Maven réutilise ses fils d'un module à l'autre : un canal laissé en place capterait
            // les avertissements du module suivant, et les écrirait dans le log de celui-ci.
            Avertissements.retablirLeDefaut();
        }
    }
}
