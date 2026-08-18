package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.Warnings;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScannerTest {

    @UMLClass
    static class ClassFixtureAnnotated {}

    static class ClassFixtureIgnored {}

    private static final Path TEST_CLASSES = Paths.get("target", "test-classes");

    private static ImmutableList<String> names(UmlProject project) {
        return project.entities().stream().map(UmlEntity::name)
            .collect(ImmutableList.toImmutableList());
    }

    @Test
    void theScan_keepsAnnotatedClassesAndLeavesTheOthers() throws Exception {
        UmlProject project = ProjectScanner.scan(TEST_CLASSES).project();

        ImmutableList<String> names = names(project);

        assertTrue(names.contains("ClassFixtureAnnotated"),
            "The class carrying @UMLClass must have its box: " + names);
        assertFalse(names.contains("ClassFixtureIgnored"),
            "The class without annotation must not have one: " + names);
    }

    @Test
    void theScan_returnsWhatItLeftAside() throws Exception {
        ScanResult result = ProjectScanner.scan(TEST_CLASSES);

        assertTrue(result.classesIgnored().contains("ClassFixtureIgnored"),
            result.classesIgnored().toString());
        assertFalse(result.classesIgnored().contains("ClassFixtureAnnotated"),
            result.classesIgnored().toString());
    }

    @Test
    void theResult_refusesANullComponent() {
        UmlProject project =
            UmlProject.given(Set.of(EntityExtractor.extract(ClassFixtureAnnotated.class)));

        assertThrows(NullPointerException.class,
            () -> new ScanResult(null, ImmutableList.of()));
        assertThrows(NullPointerException.class, () -> new ScanResult(project, null));
    }

    @Test
    void aMissingDirectory_isRefusedByNamingIt() {
        Path folderGhost = Paths.get("target", "a-directory-that-does-not-exist");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ProjectScanner.scan(folderGhost));

        assertTrue(e.getMessage().contains("a-directory-that-does-not-exist"), e.getMessage());
    }

    @Test
    void aFileInsteadOfADirectory_isRefusedByNamingIt(@TempDir Path temporaryFolder)
        throws Exception {
        Path fileSimple = temporaryFolder.resolve("text.txt");
        Files.writeString(fileSimple, "Content of an ordinary file");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ProjectScanner.scan(fileSimple));

        assertTrue(e.getMessage().contains("text.txt"), e.getMessage());
    }

    @Test
    void aNullPath_isRefused() {
        assertThrows(NullPointerException.class, () -> ProjectScanner.scan(null));
    }

    @Test
    void aNullClasspath_isRefused() {
        assertThrows(NullPointerException.class, () -> ProjectScanner.scan(TEST_CLASSES, null));
    }

    private static String saysBy(Runnable report) {
        List<String> said = new ArrayList<>();
        try (Warnings.Channel channel = Warnings.redirectTo(said::add)) {
            report.run();
        }
        assertEquals(1, said.size(), said.toString());
        return said.get(0);
    }

    @Test
    void noLostBox_saysNothing() {
        List<String> said = new ArrayList<>();
        try (Warnings.Channel channel = Warnings.redirectTo(said::add)) {
            assertFalse(ProjectScanner.reportLostBoxes(ImmutableList.of()));
        }

        assertEquals(List.of(), said);
    }

    @Test
    void aLostBox_isNamedWithItsCause() {
        String says = saysBy(() -> ProjectScanner.reportLostBoxes(ImmutableList.of(
            "com.example.Holder (java.lang.TypeNotPresentException: Type com.ex.B not present)")));

        assertTrue(says.startsWith("1 class(es) loaded but could not be read"), says);
        assertTrue(says.contains("com.example.Holder"), says);
        assertTrue(says.contains("Type com.ex.B not present"), says);
        assertFalse(says.contains("only readable on a loaded class"), says);
    }

    @Test
    void tooManyLostBoxes_areCountedAndNotNamed() {
        ImmutableList<String> too =
            IntStream.rangeClosed(0, ProjectScanner.THRESHOLD_ENUMERATION)
                .mapToObj(i -> "com.example.C" + i)
                .collect(ImmutableList.toImmutableList());

        String says = saysBy(() -> ProjectScanner.reportLostBoxes(too));

        assertTrue(says.startsWith(too.size() + " class(es) loaded but could not be read"), says);
        assertFalse(says.contains("com.example.C0"), says);
    }
}
