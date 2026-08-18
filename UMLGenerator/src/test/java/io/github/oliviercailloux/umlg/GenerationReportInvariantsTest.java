package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GenerationReportInvariantsTest {

    private static final ImmutableList<Path> ONE_FILE =
        ImmutableList.of(Path.of("diagrams", "project.svg"));

    @Test
    void aNullIgnoredClassesFile_isRefused() {
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
