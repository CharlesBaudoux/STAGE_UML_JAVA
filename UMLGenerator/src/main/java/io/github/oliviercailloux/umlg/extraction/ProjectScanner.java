package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.model.UmlView;
import io.github.oliviercailloux.umlg.warning.Warner;
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

public class ProjectScanner {
    private ProjectScanner() {}

    public static ScanResult scan(Path rootPath) throws IOException {
        return scan(rootPath, ImmutableList.of());
    }

    public static ScanResult scan(Path rootPath, ImmutableList<Path> classpath)
        throws IOException {
        checkNotNull(rootPath);
        checkNotNull(classpath);
        checkArgument(Files.exists(rootPath) && Files.isDirectory(rootPath),
            "The source directory is not valid: %s", rootPath);

        Rules rules = Rules.project(rootPath);

        Harvest harvest = traverse(rootPath, classpath, rules);

        reportClassesUnreadable(harvest.unreadable());
        reportLostBoxes(harvest.lost());
        refuserUnProjetSansAnnotation(rootPath, harvest);

        reportEmptyBoxes(harvest.emptyBoxes());
        reportAnonymousParameters(harvest.entities(), rules);
        reportRulesWithoutEffect(rules);

        ImmutableSet<UmlEntity> entities = harvest.entities();

        UmlDiagram diagram = new UmlDiagram(rules.title(), rules.direction(),
            rules.hideEmptyMembers());

        ImmutableList<UmlView> views =
            ViewBuilder.build(entities, diagram, rules);

        return new ScanResult(UmlProject.given(entities, diagram, views),
            harvest.ignored());
    }

    private record Harvest(ImmutableSet<UmlEntity> entities, ImmutableList<String> ignored,
        ImmutableList<String> emptyBoxes, ImmutableList<String> unreadable,
        ImmutableList<String> lost) {}

    private static Harvest traverse(Path rootPath, ImmutableList<Path> classpath, Rules rules)
        throws IOException {
        ImmutableSet.Builder<UmlEntity> projectBuilder = ImmutableSet.builder();

        List<String> ignored = new ArrayList<>();

        List<String> emptyBoxes = new ArrayList<>();

        List<String> unreadable = new ArrayList<>();

        List<String> lost = new ArrayList<>();

        try (URLClassLoader classLoader = loaderFor(rootPath, classpath)) {
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(EXTENSION_CLASS))
                     .forEach(classFilePath -> {
                         String className = className(rootPath, classFilePath);

                         if (className.endsWith("module-info")) {
                             return;
                         }

                         Class<?> clazz;
                         try {
                             clazz = classLoader.loadClass(className);
                         } catch (ClassNotFoundException | LinkageError e) {
                             unreadable.add(className + " (" + e + ")");
                             return;
                         }

                         try {
                             if (clazz.isAnnotationPresent(UMLClass.class)) {
                                 UmlEntity entity = EntityExtractor.extract(clazz, rules);
                                 boolean empty = emptyBoxForLackOfStrategy(clazz, entity, rules);
                                 projectBuilder.add(entity);
                                 if (empty) {
                                     emptyBoxes.add(clazz.getSimpleName());
                                 }
                             } else if (!clazz.isSynthetic() && !clazz.isAnonymousClass()) {
                                 ignored.add(clazz.getSimpleName());
                             }
                         } catch (LinkageError | TypeNotPresentException
                             | AnnotationFormatError e) {
                             lost.add(className + " (" + e + ")");
                         }
                     });
            }
        }

        return new Harvest(
            projectBuilder.build().stream()
                .sorted(Comparator.comparing(UmlEntity::qualifiedName))
                .collect(ImmutableSet.toImmutableSet()),
            ImmutableList.sortedCopyOf(ignored),
            ImmutableList.sortedCopyOf(emptyBoxes),
            ImmutableList.sortedCopyOf(unreadable),
            ImmutableList.sortedCopyOf(lost));
    }

    private static URLClassLoader loaderFor(Path rootPath, ImmutableList<Path> classpath)
        throws IOException {
        URL[] urls = new URL[classpath.size() + 1];
        urls[0] = rootPath.toUri().toURL();
        for (int i = 0; i < classpath.size(); i++) {
            urls[i + 1] = classpath.get(i).toUri().toURL();
        }
        return new URLClassLoader(urls, ProjectScanner.class.getClassLoader());
    }

    private static String className(Path rootPath, Path classFilePath) {
        String path = rootPath.relativize(classFilePath)
            .toString()
            .replace(File.separatorChar, '.');
        return path.substring(0, path.length() - EXTENSION_CLASS.length());
    }

    private static final String EXTENSION_CLASS = ".class";

    private static void refuserUnProjetSansAnnotation(Path rootPath, Harvest harvest) {
        if (!harvest.entities().isEmpty()) {
            return;
        }
        ImmutableList<String> views = harvest.ignored();
        String whatWeSaw = views.isEmpty()
            ? "no class at all was found there"
            : views.size() + " class(es) were read, none of them annotated"
                + (views.size() <= THRESHOLD_ENUMERATION ? " : " + String.join(", ", views) : "");
        throw new IllegalArgumentException("No class carries @UMLClass in " + rootPath
            + ", so there is nothing to draw — " + whatWeSaw
            + ". Annotate the classes the diagram is about, or point the analysis at the "
            + "directory where their .class files are.");
    }

    static final int THRESHOLD_ENUMERATION = 12;

    static boolean reportClassesUnreadable(ImmutableList<String> unreadable) {
        if (unreadable.isEmpty()) {
            return false;
        }
        String list = unreadable.size() <= THRESHOLD_ENUMERATION
            ? " : " + String.join(", ", unreadable)
            : "";
        Warner.say(unreadable.size() + " class(es) could not be loaded and "
            + "were skipped" + list + ". A dependency is missing from the analysed classpath, or "
            + "the compilation is partial. Whether they carried @UMLClass cannot be known — the "
            + "annotation is only readable on a loaded class — so the diagram may be incomplete.");
        return true;
    }

    static boolean reportLostBoxes(ImmutableList<String> lost) {
        if (lost.isEmpty()) {
            return false;
        }
        String list = lost.size() <= THRESHOLD_ENUMERATION
            ? " : " + String.join(", ", lost)
            : "";
        Warner.say(lost.size() + " class(es) loaded but could not be read, and were "
            + "skipped" + list + ". The class itself was found; what is missing from the analysed "
            + "classpath is a type it names — in a member signature, in a supertype, or in an "
            + "annotation. Add that dependency to the analysed classpath, or the diagram will "
            + "lack these boxes.");
        return true;
    }

    static boolean emptyBoxForLackOfStrategy(Class<?> clazz, UmlEntity entity,
        Rules rules) {
        if (rules.strategyDeclared()
            || clazz.getAnnotation(UMLClass.class).strategy() != UMLVisibility.PROJECT) {
            return false;
        }
        if (!entity.literals().isEmpty() || !entity.fields().isEmpty()
            || !entity.methods().isEmpty()) {
            return false;
        }
        return Arrays.stream(clazz.getDeclaredFields()).anyMatch(f -> !f.isSynthetic())
            || Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> !m.isSynthetic());
    }

    static boolean reportEmptyBoxes(ImmutableList<String> names) {
        if (names.isEmpty()) {
            return false;
        }

        String list = names.size() <= 12 ? " (" + String.join(", ", names) + ")" : "";

        Warner.say(names.size() + " box(es) have no member" + list
            + ": no strategy is declared, and the default draws none. Write "
            + "\"strategy\": \"public\" in umlg.json, declare one with @UMLClass(strategy = ...), "
            + "or pick members one by one with @UMLInclude.");
        return true;
    }

    static boolean reportAnonymousParameters(Collection<UmlEntity> entities,
        Rules rules) {
        if (rules.parametersOmitNames()) {
            return false;
        }
        List<UmlParameter> parameters = entities.stream()
            .flatMap(entity -> entity.methods().stream())
            .flatMap(method -> method.parameters().stream())
            .toList();

        if (parameters.isEmpty() || parameters.stream().anyMatch(UmlParameter::isNamed)) {
            return false;
        }
        Warner.say("no method parameter carries its name: the classes were "
            + "compiled without the -parameters option, so signatures show types only — "
            + "\"entries(String)\" instead of \"entries(xKeyName : String)\". Add "
            + "<maven.compiler.parameters>true</maven.compiler.parameters> to the pom (or "
            + "-parameters to javac), or say it is on purpose with \"omit\": [\"parameter names\"] "
            + "in umlg.json.");
        return true;
    }

    static boolean reportRulesWithoutEffect(Rules rules) {
        ImmutableList<String> dead = rules.rulesWithoutEffect();
        if (dead.isEmpty()) {
            return false;
        }
        Warner.say(dead.size() + " rule(s) of " + Rules.FILE_NAME
            + " matched nothing in this project: \"" + String.join("\", \"", dead)
            + "\". A renamed class or a typo leaves a dead rule behind; fix the name, or remove "
            + "the entry.");
        return true;
    }
}
