package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.Avertissements;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.model.UmlView;
import java.io.File;
import java.io.IOException;
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

    public static ResultatDeScan scan(Path rootPath) throws IOException {
        return scan(rootPath, ImmutableList.of());
    }

    // La même analyse, avec les dépendances du projet analysé en plus du répertoire lui-même.
    //
    // La réflexion ne se contente pas des .class du répertoire : charger une classe résout son
    // parent et ses interfaces, lire un attribut résout son type — et tout cela peut vivre dans
    // les jars du projet. Le classpath vide reste le cas ordinaire, celui d'un projet lancé dans
    // sa propre JVM, où ces jars y sont déjà ; le plugin Maven, lui, tourne dans la JVM de Maven
    // et doit les apporter.
    public static ResultatDeScan scan(Path rootPath, ImmutableList<Path> classpath)
        throws IOException {
        checkNotNull(rootPath);
        checkNotNull(classpath);
        checkArgument(Files.exists(rootPath) && Files.isDirectory(rootPath),
            "The source directory is not valid: %s", rootPath);

        ImmutableSet.Builder<UmlEntity> projectBuilder = ImmutableSet.builder();

        // Les classes rencontrées et laissées de côté, faute de @UMLClass. Leur nombre est la
        // seule façon, pour l'utilisateur, de distinguer une classe volontairement hors du
        // diagramme d'une classe qu'il a oublié d'annoter : les deux se ressemblent trait pour
        // trait sur le dessin, c'est-à-dire qu'elles n'y sont ni l'une ni l'autre.
        java.util.List<String> ignorees =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        // Les classes annotées dont la boîte sort vide faute d'une stratégie déclarée : la
        // surprise la plus fréquente d'un premier essai, signalée en fin de scan.
        java.util.List<String> boitesVides =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        // Les classes que le chargeur n'a pas su lier : une dépendance « provided » absente du
        // classpath donné, une compilation partielle. Elles sont mises de côté et signalées en
        // fin de scan, non plus jetées à la figure de l'utilisateur.
        java.util.List<String> illisibles =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        // Les règles du projet, lues une fois pour toutes avant la première classe : elles valent
        // pour l'ensemble du diagramme, et les relire par classe n'aurait aucun sens. Absentes,
        // c'est un jeu vide, et le code se décrit tel qu'il est écrit.
        Regles regles = Regles.duProjet(rootPath);

        // Un chargeur dédié, enraciné sur le répertoire analysé et les dépendances données : les
        // classes de l'utilisateur ne sont pas sur notre classpath, il faut les rendre
        // chargeables. Le chargeur de cette classe sert de parent, pour que les classes de la
        // bibliothèque (dont les annotations) restent celles-ci et non des copies rechargées —
        // sinon isAnnotationPresent échouerait.
        // try-with-resources : le chargeur est fermé à la sortie, libérant les fichiers ouverts.
        URL[] urls = new URL[classpath.size() + 1];
        urls[0] = rootPath.toUri().toURL();
        for (int i = 0; i < classpath.size(); i++) {
            urls[i + 1] = classpath.get(i).toUri().toURL();
        }
        try (URLClassLoader classLoader = new URLClassLoader(urls,
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
                                 UmlEntity entite = EntityExtractor.extract(clazz, regles);
                                 projectBuilder.add(entite);
                                 if (boiteVideFauteDeStrategie(clazz, entite, regles)) {
                                     boitesVides.add(clazz.getSimpleName());
                                 }
                             } else if (!clazz.isSynthetic() && !clazz.isAnonymousClass()) {
                                 // Le code que le compilateur fabrique — classes anonymes,
                                 // lambdas — n'a jamais été écrit par personne : le compter
                                 // comme « oublié » serait un reproche absurde.
                                 ignorees.add(clazz.getSimpleName());
                             }
                         } catch (ClassNotFoundException | LinkageError e) {
                             // POURQUOI ON N'ARRÊTE PLUS TOUT ICI
                             //
                             // Charger une classe résout son parent, ses interfaces et les types
                             // de ses attributs : il suffit qu'une dépendance « provided » ou
                             // « optional » manque au classpath transmis pour qu'un .class
                             // devienne illisible. Faire échouer le scan entier rendait alors la
                             // génération impossible à cause d'une classe dont le diagramme ne
                             // voulait pas — le cas le plus fréquent sur un projet réel.
                             //
                             // On passe donc son chemin, et on le dit. Le doute est nommé dans
                             // l'avertissement : l'annotation ne se lit que sur une classe
                             // chargée, donc rien ne permet de savoir si celle-ci portait
                             // @UMLClass. Un manque signalé vaut mieux qu'une panne totale.
                             illisibles.add(className + " (" + e + ")");
                         }
                     });
            }
        }

        // Le compte des ignorées est arrêté dès la fin du parcours, et non juste avant le
        // retour : le message d'erreur qui suit s'en sert, et il est émis sur un chemin d'échec.
        ImmutableList<String> ignoreesTriees = ImmutableList.sortedCopyOf(ignorees);

        signalerClassesIllisibles(ImmutableList.sortedCopyOf(illisibles));

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

        // Le répertoire ne contient aucune classe annotée. C'est le premier essai type, et le
        // message ne se contentait jusqu'ici de le constater : le scan vient pourtant de lire ces
        // classes et connaît leurs noms. Les dire, c'est la différence entre « ça ne marche pas »
        // et « voilà celle sur laquelle écrire @UMLClass ».
        if (duCode.isEmpty()) {
            String cequonAVu = ignoreesTriees.isEmpty()
                ? "no class at all was found there"
                : ignoreesTriees.size() + " class(es) were read, none of them annotated"
                    + (ignoreesTriees.size() <= SEUIL_ENUMERATION
                        ? " : " + String.join(", ", ignoreesTriees) : "");
            throw new IllegalArgumentException("No class carries @UMLClass in " + rootPath
                + ", so there is nothing to draw — " + cequonAVu
                + ". Annotate the classes the diagram is about, or point the analysis at the "
                + "directory where their .class files are.");
        }

        // Les deux surprises d'un premier essai, dites avant le compte rendu : des boîtes vides
        // parce qu'aucune stratégie n'est déclarée, des signatures sans noms parce que le projet
        // a été compilé sans -parameters. Ni l'une ni l'autre n'est une faute — le diagramme est
        // exact — mais dans les deux cas l'utilisateur obtient moins que ce qu'il croit avoir
        // demandé, et rien d'autre ne le lui dirait.
        signalerBoitesVides(ImmutableList.sortedCopyOf(boitesVides));
        signalerParametresAnonymes(duCode, regles);
        signalerReglesSansEffet(regles);

        // Les boîtes du diagramme sont celles du code, et rien d'autre : aucune règle n'en
        // fabrique. Ce qui n'est pas dans le code n'est pas sur le dessin — c'est ce qui garantit
        // qu'une boîte ne survivra pas au renommage de la classe qu'elle décrit.
        ImmutableSet<UmlEntity> entites = duCode;

        // Les réglages du dessin d'ensemble viennent du fichier de règles, comme tout ce qui vaut
        // pour le projet entier plutôt que pour un élément.
        UmlDiagram diagramme = new UmlDiagram(regles.titre(), regles.direction(),
            regles.masquerMembresVides());

        // Les vues sont taillées ici, et non au rendu : elles dépendent des règles du projet, que
        // le scan est seul à avoir lues.
        ImmutableList<UmlView> vues =
            ConstructeurDeVues.construire(entites, diagramme, regles);

        return new ResultatDeScan(UmlProject.given(entites, diagramme, vues), ignoreesTriees);
    }

    // Au-delà de ce nombre, les classes sont comptées mais pas nommées : une liste trop longue
    // noie le message qui la porte. Le même seuil que le compte rendu de génération.
    static final int SEUIL_ENUMERATION = 12;

    // Signale les .class que le chargeur n'a pas su lier. Rend vrai si un avertissement a été
    // émis, ce dont les tests se servent.
    static boolean signalerClassesIllisibles(ImmutableList<String> illisibles) {
        if (illisibles.isEmpty()) {
            return false;
        }
        String liste = illisibles.size() <= SEUIL_ENUMERATION
            ? " : " + String.join(", ", illisibles)
            : "";
        Avertissements.dire(illisibles.size() + " class(es) could not be loaded and "
            + "were skipped" + liste + ". A dependency is missing from the analysed classpath, or "
            + "the compilation is partial. Whether they carried @UMLClass cannot be known — the "
            + "annotation is only readable on a loaded class — so the diagram may be incomplete.");
        return true;
    }

    // Cette boîte est-elle vide parce que personne n'a déclaré de stratégie ?
    //
    // Quatre conditions, et chacune écarte un cas où le silence serait juste : une ligne
    // « strategy » écrite — fût-ce « strategy none » — est un choix ; une stratégie déclarée sur
    // la classe aussi ; une boîte qui montre quelque chose n'a rien à expliquer ; et une classe
    // sans aucun membre — une interface marqueur — resterait vide sous toute stratégie, la
    // signaler accuserait la mauvaise cause.
    static boolean boiteVideFauteDeStrategie(Class<?> clazz, UmlEntity entite,
        Regles regles) {
        if (regles.strategieDeclaree()
            || clazz.getAnnotation(UMLClass.class).strategy() != UMLVisibility.PROJECT) {
            return false;
        }
        if (!entite.literals().isEmpty() || !entite.fields().isEmpty()
            || !entite.methods().isEmpty()) {
            return false;
        }
        return java.util.Arrays.stream(clazz.getDeclaredFields()).anyMatch(f -> !f.isSynthetic())
            || java.util.Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> !m.isSynthetic());
    }

    // Signale les boîtes vides faute de stratégie. Rend vrai si un avertissement a été émis, ce
    // dont les tests se servent.
    static boolean signalerBoitesVides(ImmutableList<String> noms) {
        if (noms.isEmpty()) {
            return false;
        }
        // Le même seuil que le compte rendu : peu de noms se lisent, beaucoup se comptent.
        String liste = noms.size() <= 12 ? " (" + String.join(", ", noms) + ")" : "";

        Avertissements.dire(noms.size() + " box(es) have no member" + liste
            + ": no strategy is declared, and the default draws none. Write \"strategy public\" "
            + "in umlg.rules, declare one with @UMLClass(strategy = ...), or pick members one by "
            + "one with @UMLInclude.");
        return true;
    }

    // Signale des signatures toutes muettes sur leurs paramètres — la marque d'une compilation
    // sans -parameters. Rend vrai si un avertissement a été émis.
    //
    // La condition est stricte pour ne jamais accuser à tort : il faut qu'au moins une méthode
    // dessinée ait un paramètre, et qu'aucun paramètre d'aucune méthode n'ait de nom. Un projet
    // compilé avec l'option a tous les noms ; un projet qui les a tus par la règle « omit
    // parameter names » les a tus volontairement, et n'est pas signalé.
    static boolean signalerParametresAnonymes(java.util.Collection<UmlEntity> entites,
        Regles regles) {
        if (regles.omettreNomsDeParametres()) {
            return false;
        }
        java.util.List<UmlParameter> parametres = entites.stream()
            .flatMap(entite -> entite.methods().stream())
            .flatMap(methode -> methode.parameters().stream())
            .toList();

        if (parametres.isEmpty() || parametres.stream().anyMatch(UmlParameter::nomme)) {
            return false;
        }
        Avertissements.dire("no method parameter carries its name: the classes were "
            + "compiled without the -parameters option, so signatures show types only — "
            + "\"entries(String)\" instead of \"entries(xKeyName : String)\". Add "
            + "<maven.compiler.parameters>true</maven.compiler.parameters> to the pom (or "
            + "-parameters to javac), or say it is on purpose with \"omit parameter names\" in "
            + "umlg.rules.");
        return true;
    }

    // Signale les lignes du fichier de règles qui n'ont servi à rien. Rend vrai si un
    // avertissement a été émis.
    //
    // Les vues échouent bruyamment sur un nom introuvable ; les règles de nommage ne le peuvent
    // pas — un motif a le droit de ne viser personne. Mais une règle morte est presque toujours
    // une classe renommée ou une faute de frappe, et le no-op était jusqu'ici parfait : le
    // diagramme se dessinait, exact, sans que rien ne montre la ligne restée lettre morte.
    static boolean signalerReglesSansEffet(Regles regles) {
        ImmutableList<String> mortes = regles.reglesSansEffet();
        if (mortes.isEmpty()) {
            return false;
        }
        Avertissements.dire(mortes.size() + " rule(s) of " + Regles.NOM_DU_FICHIER
            + " matched nothing in this project: \"" + String.join("\", \"", mortes)
            + "\". A renamed class or a typo leaves a dead rule behind; fix the name, or remove "
            + "the line.");
        return true;
    }

}
