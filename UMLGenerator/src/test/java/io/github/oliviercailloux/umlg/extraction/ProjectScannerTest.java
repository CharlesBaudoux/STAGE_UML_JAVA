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

// The sorting the scanner performs, and its refusals at the door.
//
// Trick of this test: it scans target/test-classes, that is, its own compiled classes.
// The two fixtures declared below are therefore really there as .class files, which
// avoids having to build a sample directory.
class ProjectScannerTest {

    // An annotated inner class: it MUST be detected.
    @UMLClass
    static class ClassFixtureAnnotated {}

    // An UNANNOTATED inner class: it MUST be ignored.
    static class ClassFixtureIgnored {}

    private static final Path TEST_CLASSES = Paths.get("target", "test-classes");

    // The displayed names of the boxes returned by a scan: the output on which to bear the
    // assertions.
    private static ImmutableList<String> names(UmlProject project) {
        return project.entities().stream().map(UmlEntity::name)
            .collect(ImmutableList.toImmutableList());
    }

    @Test
    void theScan_keepsAnnotatedClassesAndLeavesTheOthers() throws Exception {
        UmlProject project = ProjectScanner.scan(TEST_CLASSES).project();

        // The directory holds the fixtures of the whole suite: one cannot compare
        // the set of boxes, but one can say what they contain and do not contain
        // not — the two halves of the sorting.
        ImmutableList<String> names = names(project);

        assertTrue(names.contains("ClassFixtureAnnotated"),
            "The class carrying @UMLClass must have its box: " + names);
        assertFalse(names.contains("ClassFixtureIgnored"),
            "The class without annotation must not have one: " + names);
    }

    @Test
    void theScan_returnsWhatItLeftAside() throws Exception {
        // What the thread variable carried, now in the result: the unannotated class
        // is not in the model, and that is precisely what there is to say about it.
        ScanResult result = ProjectScanner.scan(TEST_CLASSES);

        assertTrue(result.classesIgnored().contains("ClassFixtureIgnored"),
            result.classesIgnored().toString());
        assertFalse(result.classesIgnored().contains("ClassFixtureAnnotated"),
            result.classesIgnored().toString());
    }

    @Test
    void theResult_refusesANullComponent() {
        // A scan with no ignored class is said with an empty list, never with null: that is what
        // which lets the caller read the result without testing it.
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

        // The refusal must say which path was read: it is almost always a badly
        // computed, and the message is the only place where the user can notice it.
        assertTrue(e.getMessage().contains("a-directory-that-does-not-exist"), e.getMessage());
    }

    // @TempDir: JUnit creates a temporary directory before the test and removes it after. Nothing to
    // clean up, and two runs cannot get in each other's way.
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

    // --- The warning about boxes lost on reading ---
    //
    // It looks like the one for unreadable classes and does not say the same: that one holds for
    // a .class the loader refused, this one for a loaded class whose reading stumbled
    // on a missing type. That it is said at the right moment is exercised end to end by
    // ProjectScannerIntegrationTest; here, its text.

    // What a reporting method writes, rather than what it returns.
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
        // The complaint of the other message has no place here: the class did load.
        assertFalse(says.contains("only readable on a loaded class"), says);
    }

    @Test
    void tooManyLostBoxes_areCountedAndNotNamed() {
        // Beyond the threshold, a list of names would drown the message carrying it. It is the same
        // threshold as for the unreadable classes, hence the same rule.
        ImmutableList<String> too =
            IntStream.rangeClosed(0, ProjectScanner.THRESHOLD_ENUMERATION)
                .mapToObj(i -> "com.example.C" + i)
                .collect(ImmutableList.toImmutableList());

        String says = saysBy(() -> ProjectScanner.reportLostBoxes(too));

        assertTrue(says.startsWith(too.size() + " class(es) loaded but could not be read"), says);
        assertFalse(says.contains("com.example.C0"), says);
    }
}
