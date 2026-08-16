package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// The two components the report gained, and the absence it no longer says with a null.
//
// GenerationReportTest, next door, bears on what summary() displays. This file bears on the
// the record form: what it refuses, and what a legitimate absence is allowed to mean.
class GenerationReportInvariantsTest {

    private static final ImmutableList<Path> ONE_FILE =
        ImmutableList.of(Path.of("diagrams", "project.svg"));

    @Test
    void aNullIgnoredClassesFile_isRefused() {
        // It was the only null of the whole exported area: nothing, in the signature, warned
        // the caller that it had to be expected. Absence is now said with an empty Optional.
        assertThrows(NullPointerException.class, () -> new GenerationReport(1,
            ImmutableList.of(), ONE_FILE, null, ImmutableList.of()));
    }

    @Test
    void nullWarnings_areRefused() {
        assertThrows(NullPointerException.class, () -> new GenerationReport(1,
            ImmutableList.of(), ONE_FILE, Optional.empty(), null));
    }

    @Test
    void theShortForm_mentionsNeitherIgnoredFileNorWarning() {
        // The convenience constructor, that of a report built by hand: the two
        // added components mean "nothing to say" there, and not "we do not know".
        GenerationReport report =
            new GenerationReport(3, ImmutableList.of(), ONE_FILE);

        assertEquals(Optional.empty(), report.ignoredFile());
        assertEquals(ImmutableList.of(), report.warnings());
    }

    @Test
    void withoutIgnoredClassesFile_theSummaryDoesNotMentionIt() {
        String summary = new GenerationReport(3, ImmutableList.of(), ONE_FILE).summary();

        assertFalse(summary.contains("full list"), summary);
    }

    @Test
    void withAnIgnoredClassesFile_theSummaryNamesIt() {
        // The line saying where to find the list belongs to the returned report, and not to the output
        // standard: a caller relaying it elsewhere — the Maven plugin in its log —
        // was losing along the way.
        Path list = Path.of("diagrams", UMLGenerator.CLASSES_IGNORED_FILE);

        String summary = new GenerationReport(3, ImmutableList.of("Forgotten"), ONE_FILE,
            Optional.of(list), ImmutableList.of()).summary();

        assertTrue(summary.contains(list.toString()), summary);
    }

    @Test
    void theWarnings_areReturnedAsIs() {
        GenerationReport report = new GenerationReport(3, ImmutableList.of(), ONE_FILE,
            Optional.empty(), ImmutableList.of("the view is unreadable", "rule without effect"));

        assertEquals(ImmutableList.of("the view is unreadable", "rule without effect"),
            report.warnings());
    }
}
