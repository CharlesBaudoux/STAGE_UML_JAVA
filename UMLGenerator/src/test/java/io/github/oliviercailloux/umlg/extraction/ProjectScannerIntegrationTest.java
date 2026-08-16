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

// The scan on real compiled classes, and not on those of the test.
//
// The procedure is the same in each test: a .java file is written in a
// temporary, it is compiled for real with javac, then the output directory is scanned.
// It is the only way of exercising what exists at that level only — the path → name translation
// qualified name, nested packages, unreadable files, non-.class resources.
class ProjectScannerIntegrationTest {

    // The javac harness — write, compile, scan — lives in CompilationWithoutProcessor, which
    // explains along the way why those fixtures are compiled without the processor.

    // Reading shortcut: the displayed names of the entities, to compare at once with Set.of.
    private static Set<String> names(UmlProject project) {
        return project.entities().stream().map(UmlEntity::name).collect(Collectors.toSet());
    }

    @Test
    void classInANestedPackage_isDetectedWithItsSimpleName(@TempDir Path tmp) throws Exception {
        // Checks the "file path → qualified name" translation: without it, the
        // classLoader would not find the class again and the scan would raise IllegalStateException.
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
        // Inner classes produce an "Outer$Inner.class" file: the name
        // the qualified name must keep the '$' to stay loadable.
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
        // A real build directory also holds resources (.properties, .txt, .xml).
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
        // UmlProject requires at least one entity: scanning a project where nobody annotated
        // must produce an understandable error, not an empty diagram.
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Nothing.java", """
            package com.example;

            public class Nothing {}
            """);

        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(output).project());

        // The message does not merely state the fact: the scan has just read this class, it
        // knows its name, and that is the one to annotate.
        assertTrue(e.getMessage().contains("No class carries @UMLClass"), e.getMessage());
        assertTrue(e.getMessage().contains("Nothing"),
            "The message must name the classes read: " + e.getMessage());
    }

    // An existing but empty directory: the refusal comes from UmlProject, not from the scan itself.
    @Test
    void emptyDirectory_isRefused(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(tmp).project());
    }

    @Test
    void unreadableClassFile_isIgnoredWithoutStoppingTheScan(@TempDir Path tmp)
        throws Exception {
        // A corrupt .class, compiled for a newer JVM, or one of whose dependencies is missing from the
        // classpath, raises a LinkageError on loading. The scan must go its way rather
        // than aborting: the unreadable class is very probably not the one the diagram
        // describes, and failing the whole generation over it made the tool unusable on
        // a real project where a "provided" dependency is missing.
        Path output = tmp.resolve("classes");
        compiler(tmp.resolve("src"), output, "com/example/Bonne.java", """
            package com.example;

            import io.github.oliviercailloux.umlg.annotation.UMLClass;

            @UMLClass
            public class Bonne {}
            """);
        Files.createDirectories(output.resolve("com/example"));
        // The first four bytes are the magic number of a .class (0xCAFEBABE): the
        // the file is therefore recognized as such, but what follows means nothing.
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
        // The edge case of the previous one: if the only class of the directory is unreadable, there is
        // nothing is left to draw. The refusal must stay, otherwise the user would receive a
        // diagram empty without savoir why.
        Path output = tmp.resolve("classes");
        Files.createDirectories(output.resolve("com/example"));
        Files.write(output.resolve("com/example/Corrompue.class"),
            new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0, 1, 2, 3});

        assertThrows(IllegalArgumentException.class, () -> ProjectScanner.scan(output).project());
    }

    // --- A dependency missing from the analysed classpath ---

    // Compiles an annotated class naming a type declared in the same file, then removes the
    // .class of that type: what remains is exactly a project whose dependency is missing from the
    // analysed classpath — the case of a forgotten "provided" dependency. A second class, sound
    // and independent, serves to check that the scan went all the way.
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

    // What the scan said along the way. The channel is restored in a finally: left in place, it
    // would capture the warnings of the next test, which runs on the same thread.
    private static List<String> scanWarnings(Path output) throws IOException {
        List<String> said = new ArrayList<>();
        try (Warnings.Channel channel = Warnings.redirectTo(said::add)) {
            ProjectScanner.scan(output);
        }
        return said;
    }

    // The single warning naming the given class.
    private static String warningOn(List<String> said, String clazz) {
        List<String> kept = said.stream().filter(m -> m.contains(clazz)).toList();
        assertEquals(1, kept.size(), "One only warning must nommer " + clazz + " : "
            + said);
        return kept.get(0);
    }

    @Test
    void typeAbsentFromAGenericSignature_doesNotFailTheScan(@TempDir Path tmp)
        throws Exception {
        // getGenericType() on "List<Missing>" does not raise NoClassDefFoundError but
        // a TypeNotPresentException, a RuntimeException escaping the catch clause: the
        // the whole scan aborted, and the message named neither the class, nor the file, nor the
        // remedy. The same directory with a non-generic field did pass — the shape of the
        // the signature decided the fate of the scan.
        Path output = dependencyRemoved(tmp, "java.util.List<Missing> list;");

        UmlProject project = ProjectScanner.scan(output).project();

        assertEquals(Set.of("Saine"), names(project),
            "The scan must deliver the boxes it could read, and do without the other.");
    }

    @Test
    void typeAbsentFromABareSignature_doesNotFailTheScan(@TempDir Path tmp) throws Exception {
        // The counterpart of the previous one: here reflection raises a LinkageError, already caught.
        // Both forms must share the same fate, otherwise nothing lets one predict
        // which of the two directories will be drawn.
        Path output = dependencyRemoved(tmp, "Missing field;");

        UmlProject project = ProjectScanner.scan(output).project();

        assertEquals(Set.of("Saine"), names(project));
    }

    @Test
    void aLoadedButUnreadableClass_isReportedWithoutClaimingItDidNotLoad(@TempDir Path tmp)
        throws Exception {
        // The message for unreadable classes says "we cannot know whether they carried
        // @UMLClass, the annotation is read only on a loaded class". On Holder, it would be
        // wrong word for word: it loaded, the annotation was read, it was there, and that is
        // its box that is lost. The message must therefore be the other one.
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
        // The other half of the split: a .class the loader refuses does keep the
        // a message naming the doubt about @UMLClass, the only case where that doubt exists.
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
        // The edge case: if the only annotated class of the directory is unreadable at extraction,
        // there is nothing left to draw. The refusal must stay — but preceded by the warning that
        // gives the cause, otherwise the user would read only a "no annotated class"
        // unreadable on a directory where it has just written one.
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
        // The diagram must not depend on the traversal order of the file system.
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
        // The validation of @UMLClass (hexadecimal colour) must also apply when the
        // class is discovered by the scan, and not only on a direct call to extract().
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

    // --- The project rules file ---

    @Test
    void rulesFile_isAppliedToTheScannedProject(@TempDir Path tmp) throws Exception {
        // The file lives at the root of the analysed directory, next to the .class files — that is, for
        // a Maven project, in src/main/resources, which the build copies there.
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

    // --- Parameter names, when the project was not compiled to keep them ---

    @Test
    void projectCompiledWithoutParameters_showsTypesOnly(@TempDir Path tmp) throws Exception {
        // The fixtures of this test are compiled without the "-parameters" option (see compiler),
        // that is, like most projects. Reflection then returns only "arg0",
        // "arg1"… and the library must stick to the types rather than writing on the
        // on the drawing names nobody chose.
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
        // A module-info.class is not a class: loading it raises "access_flag ACC_MODULE
        // is set", and the whole scan failed on the first modular project met. No
        // fixture carried one, and the defect appeared only when analysing a real project.
        Path sources = tmp.resolve("src");
        Path output = tmp.resolve("classes");
        compiler(sources, output, "com/example/Invoice.java", ONE_BOX);

        // The module descriptor is compiled apart: it needs nothing, and its only
        // its presence in the directory is enough to reproduce the case.
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
        // Files.walk returns files in file-system order, which no standard
        // defines: without sorting, two machines would produce two different diagrams. The order of the
        // boxes governs the layout, and the views draw the order of their links from it
        // invisible — so this is no theoretical nuisance.
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

    // --- The overall settings, read in the rules file ---

    // An annotated class, so that the project is not empty: the overall settings set
    // the drawing, they do not populate it.
    private static final String ONE_BOX = """
        package com.example;

        import io.github.oliviercailloux.umlg.annotation.UMLClass;

        @UMLClass
        public class Invoice {}
        """;

}
