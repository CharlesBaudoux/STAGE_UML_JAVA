package fr.dauphine.uml;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

final class ProjectScanner {

    private ProjectScanner() {} 

    static UmlProject scan(Path rootPath) throws Exception {
        checkNotNull(rootPath);
        checkArgument(Files.exists(rootPath) && Files.isDirectory(rootPath), 
            "Le dossier source n'est pas valide : %s", rootPath);

        ImmutableSet.Builder<UmlEntity> projectBuilder = ImmutableSet.builder();

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{rootPath.toUri().toURL()}, ProjectScanner.class.getClassLoader())) {
            
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".class"))
                     .forEach(classFilePath -> {
                         try {
                             String className = rootPath.relativize(classFilePath)
                                     .toString()
                                     .replace(File.separatorChar, '.')
                                     .replace(".class", "");

                             Class<?> clazz = classLoader.loadClass(className);

                             if (clazz.isAnnotationPresent(UMLClass.class)) {
                                 projectBuilder.add(UmlEntity.from(clazz));
                             }
                         } catch (ClassNotFoundException | NoClassDefFoundError e) {
                             
                         }
                     });
            }
        }

        return UmlProject.given(projectBuilder.build());
    }
}