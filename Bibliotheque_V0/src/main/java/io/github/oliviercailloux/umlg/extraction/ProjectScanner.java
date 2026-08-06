package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

// Parcourt un répertoire de classes compilées et en tire le projet UML.
//
// Le point de départ est un répertoire de .class (typiquement target/classes), et non de
// sources .java : toute la bibliothèque travaille par réflexion, donc sur du code déjà compilé.
public class ProjectScanner {

    private ProjectScanner() {}

    public static UmlProject scan(Path rootPath) throws Exception {
        checkNotNull(rootPath);
        checkArgument(Files.exists(rootPath) && Files.isDirectory(rootPath),
            "The source directory is not valid: %s", rootPath);

        ImmutableSet.Builder<UmlEntity> projectBuilder = ImmutableSet.builder();

        // Les package-info porteurs de @UMLDiagram, par nom de paquetage.
        //
        // Une TreeMap, et non l'ordre du parcours : Files.walk ne promet pas le même ordre d'un
        // système de fichiers à l'autre, et le message d'erreur qui nomme les paquetages
        // concurrents doit se lire pareil partout. Ils sont accumulés puis départagés après le
        // parcours, faute de quoi refuser le second reviendrait à refuser celui que le hasard
        // aura vu en dernier.
        Map<String, UmlDiagram> diagrammes = new TreeMap<>();

        // Les règles du projet, lues une fois pour toutes avant la première classe : elles valent
        // pour l'ensemble du diagramme, et les relire par classe n'aurait aucun sens. Absentes,
        // c'est un jeu vide, et le code se décrit tel qu'il est écrit.
        Regles regles = Regles.duProjet(rootPath);

        // Un chargeur dédié, enraciné sur le répertoire analysé : les classes de l'utilisateur
        // ne sont pas sur notre classpath, il faut les rendre chargeables. Le chargeur de cette
        // classe sert de parent, pour que les classes de la bibliothèque (dont les annotations)
        // restent celles-ci et non des copies rechargées — sinon isAnnotationPresent échouerait.
        // try-with-resources : le chargeur est fermé à la sortie, libérant les fichiers ouverts.
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {rootPath.toUri().toURL()},
            ProjectScanner.class.getClassLoader())) {

            // Files.walk descend récursivement ; le flux se ferme lui aussi, sous peine de
            // fuite de descripteurs de fichiers.
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".class"))
                     .forEach(classFilePath -> {
                         // Du chemin au nom qualifié : le chemin relatif à la racine, les
                         // séparateurs de répertoires devenus points, l'extension retirée.
                         // Ex. : com/banque/Compte.class -> com.banque.Compte
                         String className = rootPath.relativize(classFilePath)
                                 .toString()
                                 .replace(File.separatorChar, '.')
                                 .replace(".class", "");

                         try {
                             Class<?> clazz = classLoader.loadClass(className);

                             // Le filtre principal : seules les classes annotées entrent.
                             if (clazz.isAnnotationPresent(UMLClass.class)) {
                                 projectBuilder.add(EntityExtractor.extract(clazz, regles));
                             }
                             // Un package-info porteur de @UMLDiagram n'est pas une boîte : il
                             // règle le dessin dans son ensemble. Les deux tests ne s'excluent
                             // pas en théorie, mais @UMLClass ne vise pas un paquetage et
                             // @UMLDiagram ne vise que lui : aucun fichier ne passe les deux.
                             if (DiagramExtractor.porteLeDiagramme(clazz)) {
                                 diagrammes.put(clazz.getPackageName(),
                                     DiagramExtractor.extraire(clazz));
                             }
                         } catch (ClassNotFoundException | LinkageError e) {
                             // Un .class illisible signale un répertoire incohérent (dépendance
                             // manquante, compilation partielle). On enveloppe en exception non
                             // vérifiée, forcée par la signature de forEach, en conservant le
                             // fichier fautif et la cause d'origine.
                             throw new IllegalStateException(
                                 "Scan failed on class \"" + className + "\" (file: "
                                     + classFilePath + "): " + e, e);
                         }
                     });
            }
        }

        // Le constructeur d'UmlProject refuse un ensemble vide : c'est là que se signale un
        // répertoire sans aucune classe annotée.
        return UmlProject.given(projectBuilder.build(), diagramme(diagrammes));
    }

    // Les réglages d'ensemble du projet, à partir de ce que le parcours a trouvé.
    //
    // Aucun @UMLDiagram est le cas courant, et il ne se signale pas : l'annotation est faite pour
    // n'être écrite que si l'on a quelque chose à en dire.
    //
    // Deux, en revanche, arrêtent la génération. Ils décrivent deux diagrammes concurrents — deux
    // titres, deux sens de lecture — et rien ne dirait lequel fait foi ; en retenir un en silence
    // reviendrait à choisir à la place de l'auteur, dans un projet qui se contredit. Le message
    // nomme les paquetages fautifs, seule information dont l'utilisateur ait besoin pour trancher.
    private static UmlDiagram diagramme(Map<String, UmlDiagram> diagrammes) {
        checkArgument(diagrammes.size() <= 1,
            "Several packages carry @UMLDiagram: %s. The annotation sets the diagram as a whole, "
                + "so exactly one package may carry it. Keep the one that describes the diagram "
                + "you want.",
            String.join(", ", diagrammes.keySet()));

        return diagrammes.isEmpty()
            ? UmlDiagram.defaut()
            : diagrammes.values().iterator().next();
    }
}
