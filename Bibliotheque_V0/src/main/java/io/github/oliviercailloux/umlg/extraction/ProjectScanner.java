package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.model.UmlView;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
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

        // Les classes rencontrées et laissées de côté, faute de @UMLClass. Leur nombre est la
        // seule façon, pour l'utilisateur, de distinguer une classe volontairement hors du
        // diagramme d'une classe qu'il a oublié d'annoter : les deux se ressemblent trait pour
        // trait sur le dessin, c'est-à-dire qu'elles n'y sont ni l'une ni l'autre.
        java.util.List<String> ignorees =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

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

                         // module-info.class décrit un module, pas une classe : le charger lève
                         // une erreur, et un projet modulaire ferait échouer le scan entier. Il
                         // n'y a rien à en tirer pour un diagramme de classes.
                         if (className.endsWith("module-info")) {
                             return;
                         }

                         try {
                             Class<?> clazz = classLoader.loadClass(className);

                             // Le filtre principal : seules les classes annotées entrent.
                             if (clazz.isAnnotationPresent(UMLClass.class)) {
                                 projectBuilder.add(EntityExtractor.extract(clazz, regles));
                             } else if (!clazz.isSynthetic() && !clazz.isAnonymousClass()) {
                                 // Le code que le compilateur fabrique — classes anonymes,
                                 // lambdas — n'a jamais été écrit par personne : le compter
                                 // comme « oublié » serait un reproche absurde.
                                 ignorees.add(clazz.getSimpleName());
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
        //
        // L'ORDRE, QUI N'EST PAS UN DÉTAIL
        //
        // Les entités sont triées par nom complet avant d'entrer dans le projet. Files.walk rend
        // les fichiers dans l'ordre du système de fichiers, qu'aucune norme ne définit : sans ce
        // tri, deux machines analysant le même code produiraient des diagrammes différents.
        //
        // Ce n'est pas une gêne théorique. L'ordre des boîtes gouverne celui des déclarations
        // dans le fichier engendré, donc la mise en page ; et une vue qui retient tout un
        // paquetage y puise l'ordre de ses boîtes, donc les liens invisibles qui les enchaînent.
        // C'est le même souci que celui des membres, et la même réponse : entre un ordre
        // arbitraire mais stable et un ordre imprévisible, la stabilité l'emporte (voir
        // MembresOrdonnes).
        ImmutableSet<UmlEntity> duCode = projectBuilder.build().stream()
            .sorted(java.util.Comparator.comparing(UmlEntity::qualifiedName))
            .collect(ImmutableSet.toImmutableSet());

        // Les boîtes que le fichier déclare rejoignent celles du code, après elles : ce qui a du
        // code derrière vient d'abord, ce qui n'en a pas ensuite.
        ImmutableSet<UmlEntity> entites = ImmutableSet.<UmlEntity>builder()
            .addAll(duCode)
            .addAll(EntitesDeclarees.de(duCode, regles))
            .build();

        // Les réglages du dessin d'ensemble viennent du fichier de règles, comme tout ce qui vaut
        // pour le projet entier plutôt que pour un élément.
        UmlDiagram diagramme = new UmlDiagram(regles.titre(), regles.direction(),
            regles.masquerMembresVides());

        // Les vues sont taillées ici, et non au rendu : elles dépendent des règles du projet, que
        // le scan est seul à avoir lues.
        ImmutableList<UmlView> vues =
            ConstructeurDeVues.construire(entites, diagramme, regles);

        DERNIER_SCAN.set(ImmutableList.sortedCopyOf(ignorees));

        return UmlProject.given(entites, diagramme, vues);
    }

    // Les classes que le dernier scan de ce fil a laissées de côté.
    //
    // Une variable de fil plutôt qu'une valeur rendue : scan() rend un UmlProject, et lui ajouter
    // ce compte reviendrait à faire porter au modèle du diagramme une information qui ne le
    // décrit pas — ces classes n'y sont justement pas. Le fil isole les générations concurrentes,
    // et le compte rendu est lu juste après le scan qui l'a rempli.
    public static ImmutableList<String> classesIgnorees() {
        ImmutableList<String> dernieres = DERNIER_SCAN.get();
        return dernieres == null ? ImmutableList.of() : dernieres;
    }

    private static final ThreadLocal<ImmutableList<String>> DERNIER_SCAN = new ThreadLocal<>();

}
