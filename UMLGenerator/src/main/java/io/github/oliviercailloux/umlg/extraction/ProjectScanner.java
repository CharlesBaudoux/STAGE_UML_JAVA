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
import java.lang.annotation.AnnotationFormatError;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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

        // Les règles du projet, lues une fois pour toutes avant la première classe : elles valent
        // pour l'ensemble du diagramme, et les relire par classe n'aurait aucun sens. Absentes,
        // c'est un jeu vide, et le code se décrit tel qu'il est écrit.
        Regles regles = Regles.duProjet(rootPath);

        Recolte recolte = parcourir(rootPath, classpath, regles);

        // L'ordre de ce qui suit porte du sens, et il est le même qu'avant le découpage.
        //
        // Les deux avertissements du classpath incomplet — les classes qui n'ont pas chargé, et
        // celles qui ont chargé sans se laisser lire — sont dits avant le refus d'un projet sans
        // annotation : c'est souvent la même cause, et l'utilisateur doit lire l'avertissement
        // qui l'explique avant l'erreur qui le constate. Les trois autres avertissements viennent
        // après, puisqu'ils ne valent que sur un projet qui tient.
        signalerClassesIllisibles(recolte.illisibles());
        signalerBoitesPerdues(recolte.perdues());
        refuserUnProjetSansAnnotation(rootPath, recolte);

        // Les surprises d'un premier essai : des boîtes vides parce qu'aucune stratégie n'est
        // déclarée, des signatures sans noms parce que le projet a été compilé sans -parameters,
        // des règles restées lettre morte. Aucune n'est une faute — le diagramme est exact — mais
        // dans les trois cas l'utilisateur obtient moins que ce qu'il croit avoir demandé, et
        // rien d'autre ne le lui dirait.
        signalerBoitesVides(recolte.boitesVides());
        signalerParametresAnonymes(recolte.entites(), regles);
        signalerReglesSansEffet(regles);

        // Les boîtes du diagramme sont celles du code, et rien d'autre : aucune règle n'en
        // fabrique. Ce qui n'est pas dans le code n'est pas sur le dessin — c'est ce qui garantit
        // qu'une boîte ne survivra pas au renommage de la classe qu'elle décrit.
        ImmutableSet<UmlEntity> entites = recolte.entites();

        // Les réglages du dessin d'ensemble viennent du fichier de règles, comme tout ce qui vaut
        // pour le projet entier plutôt que pour un élément.
        UmlDiagram diagramme = new UmlDiagram(regles.titre(), regles.direction(),
            regles.masquerMembresVides());

        // Les vues sont taillées ici, et non au rendu : elles dépendent des règles du projet, que
        // le scan est seul à avoir lues.
        ImmutableList<UmlView> vues =
            ConstructeurDeVues.construire(entites, diagramme, regles);

        return new ResultatDeScan(UmlProject.given(entites, diagramme, vues),
            recolte.ignorees());
    }

    // Ce qu'un parcours ramène : les boîtes, et les quatre familles de ce qu'il a laissé derrière
    // lui. Les cinq collections sont déjà triées ; voir parcourir.
    //
    // illisibles et perdues se ressemblent mais ne disent pas la même chose : les premières n'ont
    // pas chargé du tout, les secondes ont chargé et n'ont pas pu être lues. Les confondre
    // faisait mentir le message ; voir signalerBoitesPerdues.
    private record Recolte(ImmutableSet<UmlEntity> entites, ImmutableList<String> ignorees,
        ImmutableList<String> boitesVides, ImmutableList<String> illisibles,
        ImmutableList<String> perdues) {}

    // Lit tous les .class du répertoire et en tire les boîtes, en notant au passage ce qui n'en
    // devient pas une.
    private static Recolte parcourir(Path rootPath, ImmutableList<Path> classpath, Regles regles)
        throws IOException {
        // UN PARCOURS SÉQUENTIEL, ET TOUT CE QUI SUIT EN DÉCOULE
        //
        // Les trois listes ci-dessous étaient enveloppées dans Collections.synchronizedList,
        // alors que le flux qui les remplit n'est pas parallèle et ne l'a jamais été. La
        // protection ne servait donc à rien — et elle induisait en erreur, car le builder
        // ci-dessous, rempli dans la même boucle, n'est pas sûr vis-à-vis des fils. Qui aurait
        // lu ces enveloppes comme une invitation à paralléliser le parcours aurait corrompu le
        // modèle sans que rien ne l'avertisse.
        //
        // Le jour où ce parcours devra être parallélisé, ce sont ces cinq collections
        // ensemble qu'il faudra traiter, et pas trois d'entre elles.
        ImmutableSet.Builder<UmlEntity> projectBuilder = ImmutableSet.builder();

        // Les classes rencontrées et laissées de côté, faute de @UMLClass. Leur nombre est la
        // seule façon, pour l'utilisateur, de distinguer une classe volontairement hors du
        // diagramme d'une classe qu'il a oublié d'annoter : les deux se ressemblent trait pour
        // trait sur le dessin, c'est-à-dire qu'elles n'y sont ni l'une ni l'autre.
        List<String> ignorees = new ArrayList<>();

        // Les classes annotées dont la boîte sort vide faute d'une stratégie déclarée : la
        // surprise la plus fréquente d'un premier essai, signalée en fin de scan.
        List<String> boitesVides = new ArrayList<>();

        // Les classes que le chargeur n'a pas su lier : une dépendance « provided » absente du
        // classpath donné, une compilation partielle. Elles sont mises de côté et signalées en
        // fin de scan, non plus jetées à la figure de l'utilisateur.
        List<String> illisibles = new ArrayList<>();

        // Les classes qui se sont chargées, mais dont la lecture a buté sur un type absent — dans
        // une signature, dans un supertype, dans une annotation. Leur boîte est perdue, ce qui
        // n'est pas la même panne que la précédente et ne se dit donc pas de la même façon.
        List<String> perdues = new ArrayList<>();

        try (URLClassLoader classLoader = chargeurPour(rootPath, classpath)) {

            // Files.walk descend récursivement ; le flux se ferme lui aussi, sous peine de
            // fuite de descripteurs de fichiers.
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(EXTENSION_CLASSE))
                     .forEach(classFilePath -> {
                         String className = nomDeClasse(rootPath, classFilePath);

                         // module-info.class décrit un module, pas une classe : le charger lève
                         // une erreur, et un projet modulaire ferait échouer le scan entier. Il
                         // n'y a rien à en tirer pour un diagramme de classes.
                         if (className.endsWith("module-info")) {
                             return;
                         }

                         // DEUX ÉTAPES, DEUX DIAGNOSTICS
                         //
                         // Le chargement et la lecture butent tous deux sur un classpath
                         // incomplet, mais pas au même moment ni avec les mêmes conséquences.
                         // Sous un try unique, une panne de lecture recevait le message du
                         // chargement — « rien ne permet de savoir si elle portait @UMLClass,
                         // l'annotation ne se lit que sur une classe chargée » —, faux mot pour
                         // mot dès lors que la classe s'était chargée et que l'annotation avait
                         // bel et bien été lue. Deux try, donc, et deux listes.
                         Class<?> clazz;
                         try {
                             clazz = classLoader.loadClass(className);
                         } catch (ClassNotFoundException | LinkageError e) {
                             // POURQUOI ON N'ARRÊTE PLUS TOUT ICI
                             //
                             // Charger une classe résout son parent et ses interfaces : il suffit
                             // qu'une dépendance « provided » ou « optional » manque au classpath
                             // transmis pour qu'un .class devienne illisible. Faire échouer le
                             // scan entier rendait alors la génération impossible à cause d'une
                             // classe dont le diagramme ne voulait pas — le cas le plus fréquent
                             // sur un projet réel.
                             //
                             // On passe donc son chemin, et on le dit. Le doute est nommé dans
                             // l'avertissement : l'annotation ne se lit que sur une classe
                             // chargée, donc rien ne permet de savoir si celle-ci portait
                             // @UMLClass. Un manque signalé vaut mieux qu'une panne totale.
                             illisibles.add(className + " (" + e + ")");
                             return;
                         }

                         try {
                             // Le filtre principal : seules les classes annotées entrent.
                             if (clazz.isAnnotationPresent(UMLClass.class)) {
                                 // La boîte n'entre qu'une fois les deux lectures faites : si la
                                 // seconde échoue, la classe est comptée perdue et rien d'elle
                                 // n'est resté dans le modèle.
                                 UmlEntity entite = EntityExtractor.extract(clazz, regles);
                                 boolean vide = boiteVideFauteDeStrategie(clazz, entite, regles);
                                 projectBuilder.add(entite);
                                 if (vide) {
                                     boitesVides.add(clazz.getSimpleName());
                                 }
                             } else if (!clazz.isSynthetic() && !clazz.isAnonymousClass()) {
                                 // Le code que le compilateur fabrique — classes anonymes,
                                 // lambdas — n'a jamais été écrit par personne : le compter
                                 // comme « oublié » serait un reproche absurde.
                                 ignorees.add(clazz.getSimpleName());
                             }
                         } catch (LinkageError | TypeNotPresentException
                             | AnnotationFormatError e) {
                             // LES TROIS FAÇONS DONT LA RÉFLEXION SIGNALE UN TYPE ABSENT
                             //
                             // LinkageError quand le type manquant est nommé nu : lire un attribut
                             // « B b » avec B hors du classpath lève NoClassDefFoundError.
                             //
                             // TypeNotPresentException quand il n'apparaît que dans une signature
                             // générique : getGenericType() sur « List<B> » ne lève pas d'erreur
                             // de liaison mais cette RuntimeException, qui n'était pas rattrapée
                             // — et le scan entier avortait, sur un message qui ne nommait ni la
                             // classe, ni le fichier, ni le remède. La forme de la signature
                             // décidait donc du sort de tout le scan, ce que rien ne laissait
                             // deviner.
                             //
                             // AnnotationFormatError quand c'est une annotation qui est mal
                             // formée : même famille de panne, même traitement.
                             perdues.add(className + " (" + e + ")");
                         }
                     });
            }
        }

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
        //
        // Les trois autres listes sont triées pour la même raison : elles finissent dans des
        // messages, et un message qui change d'ordre d'une machine à l'autre se compare mal.
        return new Recolte(
            projectBuilder.build().stream()
                .sorted(Comparator.comparing(UmlEntity::qualifiedName))
                .collect(ImmutableSet.toImmutableSet()),
            ImmutableList.sortedCopyOf(ignorees),
            ImmutableList.sortedCopyOf(boitesVides),
            ImmutableList.sortedCopyOf(illisibles),
            ImmutableList.sortedCopyOf(perdues));
    }

    // Un chargeur dédié, enraciné sur le répertoire analysé et les dépendances données : les
    // classes de l'utilisateur ne sont pas sur notre classpath, il faut les rendre chargeables.
    // Le chargeur de cette classe sert de parent, pour que les classes de la bibliothèque (dont
    // les annotations) restent celles-ci et non des copies rechargées — sinon
    // isAnnotationPresent échouerait.
    //
    // L'appelant le referme : il ouvre des fichiers, qu'un try-with-resources doit libérer.
    private static URLClassLoader chargeurPour(Path rootPath, ImmutableList<Path> classpath)
        throws IOException {
        URL[] urls = new URL[classpath.size() + 1];
        urls[0] = rootPath.toUri().toURL();
        for (int i = 0; i < classpath.size(); i++) {
            urls[i + 1] = classpath.get(i).toUri().toURL();
        }
        return new URLClassLoader(urls, ProjectScanner.class.getClassLoader());
    }

    // Du chemin au nom qualifié : le chemin relatif à la racine, les séparateurs de répertoires
    // devenus points, l'extension retirée.
    // Ex. : com/banque/Compte.class -> com.banque.Compte
    //
    // L'extension est coupée par la longueur, et non par un replace(".class", "") : celui-ci
    // remplaçait toutes les occurrences, si bien qu'un répertoire nommé « x.class.y » aurait
    // amputé le nom en son milieu. Le flux ne retient que les fichiers finissant par .class,
    // donc le suffixe est toujours là.
    private static String nomDeClasse(Path rootPath, Path classFilePath) {
        String chemin = rootPath.relativize(classFilePath)
            .toString()
            .replace(File.separatorChar, '.');
        return chemin.substring(0, chemin.length() - EXTENSION_CLASSE.length());
    }

    private static final String EXTENSION_CLASSE = ".class";

    // Le répertoire ne contient aucune classe annotée. C'est le premier essai type, et le message
    // ne se contentait jusqu'ici de le constater : le scan vient pourtant de lire ces classes et
    // connaît leurs noms. Les dire, c'est la différence entre « ça ne marche pas » et « voilà
    // celle sur laquelle écrire @UMLClass ».
    private static void refuserUnProjetSansAnnotation(Path rootPath, Recolte recolte) {
        if (!recolte.entites().isEmpty()) {
            return;
        }
        ImmutableList<String> vues = recolte.ignorees();
        String cequonAVu = vues.isEmpty()
            ? "no class at all was found there"
            : vues.size() + " class(es) were read, none of them annotated"
                + (vues.size() <= SEUIL_ENUMERATION ? " : " + String.join(", ", vues) : "");
        throw new IllegalArgumentException("No class carries @UMLClass in " + rootPath
            + ", so there is nothing to draw — " + cequonAVu
            + ". Annotate the classes the diagram is about, or point the analysis at the "
            + "directory where their .class files are.");
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

    // Signale les classes qui se sont chargées mais dont la lecture a échoué. Rend vrai si un
    // avertissement a été émis, ce dont les tests se servent.
    //
    // Ce message existe pour ne pas dire l'autre. Ici la classe a bien chargé, son annotation a
    // bien été lue : renvoyer l'utilisateur vers « l'annotation ne se lit que sur une classe
    // chargée » l'enverrait chercher au mauvais endroit. Ce qui manque n'est pas la classe, c'est
    // un type qu'elle nomme.
    static boolean signalerBoitesPerdues(ImmutableList<String> perdues) {
        if (perdues.isEmpty()) {
            return false;
        }
        String liste = perdues.size() <= SEUIL_ENUMERATION
            ? " : " + String.join(", ", perdues)
            : "";
        Avertissements.dire(perdues.size() + " class(es) loaded but could not be read, and were "
            + "skipped" + liste + ". The class itself was found; what is missing from the analysed "
            + "classpath is a type it names — in a member signature, in a supertype, or in an "
            + "annotation. Add that dependency to the analysed classpath, or the diagram will "
            + "lack these boxes.");
        return true;
    }

    // Cette boîte est-elle vide parce que personne n'a déclaré de stratégie ?
    //
    // Quatre conditions, et chacune écarte un cas où le silence serait juste : une clé
    // « strategy » écrite — fût-ce « "strategy": "none" » — est un choix ; une stratégie déclarée sur
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
        return Arrays.stream(clazz.getDeclaredFields()).anyMatch(f -> !f.isSynthetic())
            || Arrays.stream(clazz.getDeclaredMethods())
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
            + ": no strategy is declared, and the default draws none. Write "
            + "\"strategy\": \"public\" in umlg.json, declare one with @UMLClass(strategy = ...), "
            + "or pick members one by one with @UMLInclude.");
        return true;
    }

    // Signale des signatures toutes muettes sur leurs paramètres — la marque d'une compilation
    // sans -parameters. Rend vrai si un avertissement a été émis.
    //
    // La condition est stricte pour ne jamais accuser à tort : il faut qu'au moins une méthode
    // dessinée ait un paramètre, et qu'aucun paramètre d'aucune méthode n'ait de nom. Un projet
    // compilé avec l'option a tous les noms ; un projet qui les a tus par la règle « omit
    // parameter names » les a tus volontairement, et n'est pas signalé.
    static boolean signalerParametresAnonymes(Collection<UmlEntity> entites,
        Regles regles) {
        if (regles.omettreNomsDeParametres()) {
            return false;
        }
        List<UmlParameter> parametres = entites.stream()
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
            + "-parameters to javac), or say it is on purpose with \"omit\": [\"parameter names\"] "
            + "in umlg.json.");
        return true;
    }

    // Signale les entrées du fichier de règles qui n'ont servi à rien. Rend vrai si un
    // avertissement a été émis.
    //
    // Les vues échouent bruyamment sur un nom introuvable ; les règles de nommage ne le peuvent
    // pas — un motif a le droit de ne viser personne. Mais une règle morte est presque toujours
    // une classe renommée ou une faute de frappe, et le no-op était jusqu'ici parfait : le
    // diagramme se dessinait, exact, sans que rien ne montre l'entrée restée lettre morte.
    static boolean signalerReglesSansEffet(Regles regles) {
        ImmutableList<String> mortes = regles.reglesSansEffet();
        if (mortes.isEmpty()) {
            return false;
        }
        Avertissements.dire(mortes.size() + " rule(s) of " + Regles.NOM_DU_FICHIER
            + " matched nothing in this project: \"" + String.join("\", \"", mortes)
            + "\". A renamed class or a typo leaves a dead rule behind; fix the name, or remove "
            + "the entry.");
        return true;
    }

}
