package io.github.oliviercailloux.umlg.extraction;

import static io.github.oliviercailloux.umlg.CompilationWithoutProcessor.compiler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.Warnings;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScannerIntegrationTest {

    private static Set<String> names(UmlProject project) {
        return project.entities().stream().map(UmlEntity::name).collect(Collectors.toSet());
    }

    @Test
    void classInANestedPackage_isDetectedWithItsSimpleName(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/domain/Invoice.java", """
            package com.example.domain;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Invoice {
                public int amount;
                public void emit() {}
            }
            """);

        UmlProject project = ProjectScanner.scan(output).project();

        assertEquals(Set.of("Invoice"), names(project));
        UmlEntity invoice = project.entities().iterator().next();
        assertTrue(invoice.fields().stream().anyMatch(f -> f.name().equals("amount")));
        assertTrue(invoice.methods().stream().anyMatch(m -> m.name().equals("emit")));
    }

    @Test
    void unannotatedClass_isIgnored(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Annotated.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Annotated {}
            """);
        compiler(tmp.resolve("src"), output, "com/example/Ordinary.java", """
            package com.example;

            public class Ordinary {
                public int field;
            }
            """);

        UmlProject project = ProjectScanner.scan(output).project();

        assertEquals(Set.of("Annotated"), names(project),
            "Only classes carrying @UMLClass enter the diagram.");
    }

    @Test
    void annotatedInnerClass_isDetected(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/External.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            public class External {
                @UMLClass(name = "Internal")
                public static class Internal {}
            }
            """);

        UmlProject project = ProjectScanner.scan(output).project();

        assertEquals(Set.of("Internal"), names(project));
    }

    @Test
    void nonClassFiles_areIgnored(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Only.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Only {}
            """);
        Files.writeString(output.resolve("application.properties"), "key=value");
        Files.createDirectories(output.resolve("META-INF"));
        Files.writeString(output.resolve("META-INF/MANIFEST.MF"), "Manifest-Version : 1.0");

        UmlProject project = ProjectScanner.scan(output).project();

        assertEquals(Set.of("Only"), names(project));
    }

    @Test
    void directoryWithoutAnnotatedClass_isRefusedWithAnExplicitMessage(@TempDir Path tmp)
        throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Nothing.java", """
            package com.example;

            public class Nothing {}
            """);

        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(output).project());

        assertTrue(e.getMessage().contains("No class carries @UMLClass"), e.getMessage());
        assertTrue(e.getMessage().contains("Nothing"),
            "The message must name the classes read: " + e.getMessage());
    }

    @Test
    void emptyDirectory_isRefused(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(tmp).project());
    }

    @Test
    void unreadableClassFile_isIgnoredWithoutStoppingTheScan(@TempDir Path tmp)
        throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Bonne.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Bonne {}
            """);
        Files.createDirectories(output.resolve("com/example"));
        Files.write(output.resolve("com/example/Corrompue.class"),
            new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0, 1, 2, 3});

        UmlProject project = ProjectScanner.scan(output).project();

        assertEquals(1, project.entities().size());
        assertEquals("com.example.Bonne",
            project.entities().iterator().next().qualifiedName());
    }

    @Test
    void allClassesUnreadable_doNotSilentlyGiveAnEmptyDiagram(@TempDir Path tmp)
        throws Exception {
        Path output = tmp.resolve("classes");
        Files.createDirectories(output.resolve("com/example"));
        Files.write(output.resolve("com/example/Corrompue.class"),
            new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0, 1, 2, 3});

        assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(output).project());
    }

    private static Path dependencyRemoved(Path tmp, String fieldDeclaration) throws IOException {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Holder.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            class Missing {}

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Holder {
                public Field
            }
            """.replace("Field", fieldDeclaration));
        compiler(tmp.resolve("src"), output, "com/example/Saine.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Saine {}
            """);
        Files.delete(output.resolve("com/example/Missing.class"));
        return output;
    }

    private static List<String> scanWarnings(Path output) throws IOException {
        List<String> said = new ArrayList<>();
        try (Warnings.Channel channel = Warnings.redirectTo(said::add)) {
            ProjectScanner.scan(output);
        }
        return said;
    }

    private static String warningOn(List<String> said, String clazz) {
        List<String> kept = said.stream().filter(m -> m.contains(clazz)).toList();
        assertEquals(1, kept.size(), "One only warning must nommer " + clazz + " : "
            + said);
        return kept.get(0);
    }

    @Test
    void typeAbsentFromAGenericSignature_doesNotFailTheScan(@TempDir Path tmp)
        throws Exception {
        Path output = dependencyRemoved(tmp, "java.util.List<Missing> list;");

        UmlProject project = ProjectScanner.scan(output).project();

        assertEquals(Set.of("Saine"), names(project),
            "The scan must deliver the boxes it could read, and do without the other.");
    }

    @Test
    void typeAbsentFromABareSignature_doesNotFailTheScan(@TempDir Path tmp) throws Exception {
        Path output = dependencyRemoved(tmp, "Missing field;");

        UmlProject project = ProjectScanner.scan(output).project();

        assertEquals(Set.of("Saine"), names(project));
    }

    @Test
    void aLoadedButUnreadableClass_isReportedWithoutClaimingItDidNotLoad(@TempDir Path tmp)
        throws Exception {
        Path output = dependencyRemoved(tmp, "java.util.List<Missing> list;");

        String says = warningOn(scanWarnings(output), "com.example.Holder");

        assertTrue(says.contains("loaded but could not be read"), says);
        assertTrue(says.contains("a type it names"), says);
        assertFalse(says.contains("only readable on a loaded class"),
            "The class did load: that complaint does not concern it — " + says);
    }

    @Test
    void aClassThatDoesNotLoad_keepsTheUnreadableClassesMessage(@TempDir Path tmp)
        throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Saine.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Saine {}
            """);
        Files.write(output.resolve("com/example/Corrompue.class"),
            new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0, 1, 2, 3});

        String says = warningOn(scanWarnings(output), "com.example.Corrompue");

        assertTrue(says.contains("could not be loaded"), says);
        assertTrue(says.contains("only readable on a loaded class"), says);
    }

    @Test
    void allBoxesLost_doNotSilentlyGiveAnEmptyDiagram(@TempDir Path tmp)
        throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Holder.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            class Missing {}

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Holder {
                public java.util.List<Missing> list;
            }
            """);
        Files.delete(output.resolve("com/example/Missing.class"));

        List<String> said = new ArrayList<>();
        try (Warnings.Channel channel = Warnings.redirectTo(said::add)) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ProjectScanner.scan(output).project());
            assertTrue(e.getMessage().contains("No class carries @UMLClass"), e.getMessage());
        }

        assertTrue(warningOn(said, "com.example.Holder")
            .contains("loaded but could not be read"), said.toString());
    }

    @Test
    void repeatedScan_givesTheSameResult(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/A.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class A {}
            """);
        compiler(tmp.resolve("src"), output, "com/example/B.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class B {}
            """);

        assertEquals(ProjectScanner.scan(output).project().entities(),
            ProjectScanner.scan(output).project().entities());
        assertEquals(Set.of("A", "B"), names(ProjectScanner.scan(output).project()));
    }

    @Test
    void invalidAnnotationOnAScannedClass_reachesTheScan(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/MauvaiseCouleur.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass(color = "red-bright")
            public class MauvaiseCouleur {}
            """);

        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(output).project());

        assertTrue(e.getMessage().contains("red-bright"), e.getMessage());
    }

    @Test
    void rulesFile_isAppliedToTheScannedProject(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Keyboard.java", """
            package com.example;

            import com.google.common.collect.ImmutableList;
            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Keyboard {
                public ImmutableList<String> keys;
            }
            """);
        Files.writeString(output.resolve("umlg.json"), """
            {
              "_": "Guava teaches the diagram reader nothing.",
              "types": { "ImmutableList": "List" }
            }
            """, StandardCharsets.UTF_8);

        UmlEntity keyboard = ProjectScanner.scan(output).project().entities().iterator().next();

        assertEquals("List<String>", keyboard.fields().get(0).type(),
            "The project rule must reach the boxes: " + keyboard.fields());
    }

    @Test
    void withoutRulesFile_theScanDescribesTheCodeAsItIs(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Keyboard.java", """
            package com.example;

            import com.google.common.collect.ImmutableList;
            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Keyboard {
                public ImmutableList<String> keys;
            }
            """);

        UmlEntity keyboard = ProjectScanner.scan(output).project().entities().iterator().next();

        assertEquals("ImmutableList<String>", keyboard.fields().get(0).type());
    }

    @Test
    void projectCompiledWithoutParameters_showsTypesOnly(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Flight.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;
            import io.github.oliviercailloux.umlg.annotation.UMLVisibility;

            @UMLClass(strategy = UMLVisibility.PUBLIC)
            public class Flight {
                public void reporter(String newTime, int minutes) {}
            }
            """);

        UmlEntity flight = ProjectScanner.scan(output).project().entities().iterator().next();
        UmlMethod reporter = flight.methods().get(0);

        assertEquals(List.of("String", "int"),
            reporter.parameters().stream().map(UmlParameter::type).toList());
        assertTrue(reporter.parameters().stream().noneMatch(UmlParameter::isNamed),
            "No \"arg0\" must reach the drawing: " + reporter.parameters());
    }

    @Test
    void withoutRulesFile_theSettingsAreTheDefaultOnes(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Invoice.java", ONE_BOX);

        assertEquals(UmlDiagram.defaults(), ProjectScanner.scan(output).project().diagram());
    }

    @Test
    void titleAndReadingDirection_areReadInTheRulesFile(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Invoice.java", ONE_BOX);
        Files.writeString(output.resolve("umlg.json"), """
            {
              "title": "Architecture of the Billing module",
              "direction": "left-to-right",
              "showEmptyMembers": true
            }
            """, StandardCharsets.UTF_8);

        UmlDiagram diagram = ProjectScanner.scan(output).project().diagram();

        assertEquals("Architecture of the Billing module", diagram.title());
        assertEquals(UMLFlow.LEFT_TO_RIGHT, diagram.direction());
        assertFalse(diagram.hideEmptyMembers());
    }

    @Test
    void faultyRulesFile_stopsTheScan(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Invoice.java", ONE_BOX);
        Files.writeString(output.resolve("umlg.json"), "{ \"title\": \"\" }\n",
            StandardCharsets.UTF_8);

        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(output).project());

        assertTrue(e.getMessage().contains("the title cannot be empty"), e.getMessage());
    }

    @Test
    void aModularProject_doesNotFailTheScan(@TempDir Path tmp) throws Exception {
        Path sources = tmp.resolve("src");
        Path output = tmp.resolve("classes");
        compiler(sources, output, "com/example/Invoice.java", ONE_BOX);

        Path descriptor = sources.resolve("module-info.java");
        Files.writeString(descriptor, "module fixture {\n}\n", StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager =
            compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            assertTrue(compiler.getTask(null, manager, null,
                List.of("-proc:none", "-d", output.toString()), null,
                manager.getJavaFileObjects(descriptor.toFile())).call(),
                "The module descriptor must compile.");
        }
        assertTrue(Files.exists(output.resolve("module-info.class")),
            "The case is not reproduced if the descriptor is not there.");

        UmlProject project = ProjectScanner.scan(output).project();

        assertEquals(Set.of("Invoice"), names(project),
            "The module-info is ignored, and the annotated classes are indeed read.");
    }

    @Test
    void boxOrder_doesNotDependOnTheFileSystem(@TempDir Path tmp) throws Exception {
        Path sources = tmp.resolve("src");
        Path output = tmp.resolve("classes");
        for (String name : List.of("Zebra", "Alpha", "Middle")) {
            compiler(sources, output, "com/example/" + name + ".java", """
                package com.example;

                import io.github.oliviercailloux.umlg.annotation.UMLClass;

                @UMLClass
                public class Name {}
                """.replace("Name", name));
        }

        List<String> read = ProjectScanner.scan(output).project().entities().stream()
            .map(UmlEntity::name).toList();

        assertEquals(List.of("Alpha", "Middle", "Zebra"), read,
            "The boxes must be rendered in a stable order, here the alphabetical one.");
    }

    private static final String ONE_BOX = """
        package com.example;

        import io.github.oliviercailloux.umlg.annotation.UMLClass;

        @UMLClass
        public class Invoice {}
        """;

}
