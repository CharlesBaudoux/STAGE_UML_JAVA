package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class ProjectScanner {

    private ProjectScanner() {}

    public static UmlProject scan(Path rootPath) throws Exception {
        checkNotNull(rootPath);
        checkArgument(Files.exists(rootPath) && Files.isDirectory(rootPath),
            "Le dossier source n'est pas valide : %s", rootPath);

        ImmutableSet.Builder<UmlEntity> projectBuilder = ImmutableSet.builder();

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{rootPath.toUri().toURL()}, ProjectScanner.class.getClassLoader())) {

            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".class"))
                     .forEach(classFilePath -> {
                         String className = rootPath.relativize(classFilePath)
                                 .toString()
                                 .replace(File.separatorChar, '.')
                                 .replace(".class", "");

                         try {
                             Class<?> clazz = classLoader.loadClass(className);

                             if (clazz.isAnnotationPresent(UMLClass.class)) {
                                 projectBuilder.add(EntityExtractor.extract(clazz));
                             }
                         } catch (ClassNotFoundException | LinkageError e) {
                             throw new IllegalStateException(
                                 "Échec du scan sur la classe \"" + className + "\" (fichier : "
                                     + classFilePath + ") : " + e, e);
                         }
                     });
            }
        }

        return UmlProject.given(projectBuilder.build());
    }
}
