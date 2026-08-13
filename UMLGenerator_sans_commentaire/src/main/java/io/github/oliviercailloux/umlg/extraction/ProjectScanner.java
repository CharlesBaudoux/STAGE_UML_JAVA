package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
import io.github.oliviercailloux.umlg.warning.Avertisseur;
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

    public static ResultatDeScan scan(Path rootPath) throws IOException {
        return scan(rootPath, ImmutableList.of());
    }

    public static ResultatDeScan scan(Path rootPath, ImmutableList<Path> classpath)
        throws IOException {
        checkNotNull(rootPath);
        checkNotNull(classpath);
        checkArgument(Files.exists(rootPath) && Files.isDirectory(rootPath),
            "The source directory is not valid: %s", rootPath);

        Regles regles = Regles.duProjet(rootPath);

        Recolte recolte = parcourir(rootPath, classpath, regles);

        signalerClassesIllisibles(recolte.illisibles());
        signalerBoitesPerdues(recolte.perdues());
        refuserUnProjetSansAnnotation(rootPath, recolte);

        signalerBoitesVides(recolte.boitesVides());
        signalerParametresAnonymes(recolte.entites(), regles);
        signalerReglesSansEffet(regles);

        ImmutableSet<UmlEntity> entites = recolte.entites();

        UmlDiagram diagramme = new UmlDiagram(regles.titre(), regles.direction(),
            regles.masquerMembresVides());

        ImmutableList<UmlView> vues =
            ConstructeurDeVues.construire(entites, diagramme, regles);

        return new ResultatDeScan(UmlProject.given(entites, diagramme, vues),
            recolte.ignorees());
    }

    private record Recolte(ImmutableSet<UmlEntity> entites, ImmutableList<String> ignorees,
        ImmutableList<String> boitesVides, ImmutableList<String> illisibles,
        ImmutableList<String> perdues) {}

    private static Recolte parcourir(Path rootPath, ImmutableList<Path> classpath, Regles regles)
        throws IOException {
        ImmutableSet.Builder<UmlEntity> projectBuilder = ImmutableSet.builder();

        List<String> ignorees = new ArrayList<>();

        List<String> boitesVides = new ArrayList<>();

        List<String> illisibles = new ArrayList<>();

        List<String> perdues = new ArrayList<>();

        try (URLClassLoader classLoader = chargeurPour(rootPath, classpath)) {

            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(EXTENSION_CLASSE))
                     .forEach(classFilePath -> {
                         String className = nomDeClasse(rootPath, classFilePath);

                         if (className.endsWith("module-info")) {
                             return;
                         }

                         Class<?> clazz;
                         try {
                             clazz = classLoader.loadClass(className);
                         } catch (ClassNotFoundException | LinkageError e) {
                             illisibles.add(className + " (" + e + ")");
                             return;
                         }

                         try {
                             if (clazz.isAnnotationPresent(UMLClass.class)) {
                                 UmlEntity entite = EntityExtractor.extract(clazz, regles);
                                 boolean vide = boiteVideFauteDeStrategie(clazz, entite, regles);
                                 projectBuilder.add(entite);
                                 if (vide) {
                                     boitesVides.add(clazz.getSimpleName());
                                 }
                             } else if (!clazz.isSynthetic() && !clazz.isAnonymousClass()) {
                                 ignorees.add(clazz.getSimpleName());
                             }
                         } catch (LinkageError | TypeNotPresentException
                             | AnnotationFormatError e) {
                             perdues.add(className + " (" + e + ")");
                         }
                     });
            }
        }

        return new Recolte(
            projectBuilder.build().stream()
                .sorted(Comparator.comparing(UmlEntity::qualifiedName))
                .collect(ImmutableSet.toImmutableSet()),
            ImmutableList.sortedCopyOf(ignorees),
            ImmutableList.sortedCopyOf(boitesVides),
            ImmutableList.sortedCopyOf(illisibles),
            ImmutableList.sortedCopyOf(perdues));
    }

    private static URLClassLoader chargeurPour(Path rootPath, ImmutableList<Path> classpath)
        throws IOException {
        URL[] urls = new URL[classpath.size() + 1];
        urls[0] = rootPath.toUri().toURL();
        for (int i = 0; i < classpath.size(); i++) {
            urls[i + 1] = classpath.get(i).toUri().toURL();
        }
        return new URLClassLoader(urls, ProjectScanner.class.getClassLoader());
    }

    private static String nomDeClasse(Path rootPath, Path classFilePath) {
        String chemin = rootPath.relativize(classFilePath)
            .toString()
            .replace(File.separatorChar, '.');
        return chemin.substring(0, chemin.length() - EXTENSION_CLASSE.length());
    }

    private static final String EXTENSION_CLASSE = ".class";

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

    static final int SEUIL_ENUMERATION = 12;

    static boolean signalerClassesIllisibles(ImmutableList<String> illisibles) {
        if (illisibles.isEmpty()) {
            return false;
        }
        String liste = illisibles.size() <= SEUIL_ENUMERATION
            ? " : " + String.join(", ", illisibles)
            : "";
        Avertisseur.dire(illisibles.size() + " class(es) could not be loaded and "
            + "were skipped" + liste + ". A dependency is missing from the analysed classpath, or "
            + "the compilation is partial. Whether they carried @UMLClass cannot be known — the "
            + "annotation is only readable on a loaded class — so the diagram may be incomplete.");
        return true;
    }

    static boolean signalerBoitesPerdues(ImmutableList<String> perdues) {
        if (perdues.isEmpty()) {
            return false;
        }
        String liste = perdues.size() <= SEUIL_ENUMERATION
            ? " : " + String.join(", ", perdues)
            : "";
        Avertisseur.dire(perdues.size() + " class(es) loaded but could not be read, and were "
            + "skipped" + liste + ". The class itself was found; what is missing from the analysed "
            + "classpath is a type it names — in a member signature, in a supertype, or in an "
            + "annotation. Add that dependency to the analysed classpath, or the diagram will "
            + "lack these boxes.");
        return true;
    }

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

    static boolean signalerBoitesVides(ImmutableList<String> noms) {
        if (noms.isEmpty()) {
            return false;
        }
        String liste = noms.size() <= 12 ? " (" + String.join(", ", noms) + ")" : "";

        Avertisseur.dire(noms.size() + " box(es) have no member" + liste
            + ": no strategy is declared, and the default draws none. Write "
            + "\"strategy\": \"public\" in umlg.json, declare one with @UMLClass(strategy = ...), "
            + "or pick members one by one with @UMLInclude.");
        return true;
    }

    static boolean signalerParametresAnonymes(Collection<UmlEntity> entites,
        Regles regles) {
        if (regles.omettreNomsDeParametres()) {
            return false;
        }
        List<UmlParameter> parametres = entites.stream()
            .flatMap(entite -> entite.methods().stream())
            .flatMap(methode -> methode.parameters().stream())
            .toList();

        if (parametres.isEmpty() || parametres.stream().anyMatch(UmlParameter::isNamed)) {
            return false;
        }
        Avertisseur.dire("no method parameter carries its name: the classes were "
            + "compiled without the -parameters option, so signatures show types only — "
            + "\"entries(String)\" instead of \"entries(xKeyName : String)\". Add "
            + "<maven.compiler.parameters>true</maven.compiler.parameters> to the pom (or "
            + "-parameters to javac), or say it is on purpose with \"omit\": [\"parameter names\"] "
            + "in umlg.json.");
        return true;
    }

    static boolean signalerReglesSansEffet(Regles regles) {
        ImmutableList<String> mortes = regles.reglesSansEffet();
        if (mortes.isEmpty()) {
            return false;
        }
        Avertisseur.dire(mortes.size() + " rule(s) of " + Regles.NOM_DU_FICHIER
            + " matched nothing in this project: \"" + String.join("\", \"", mortes)
            + "\". A renamed class or a typo leaves a dead rule behind; fix the name, or remove "
            + "the entry.");
        return true;
    }

}
