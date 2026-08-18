package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GenerationReportTest {

    private static GenerationReport report(int read, int ignored, int files) {
        return new GenerationReport(read,
            IntStream.range(0, ignored).mapToObj(i -> "Ignored" + i)
                .collect(ImmutableList.toImmutableList()),
            IntStream.range(0, files).mapToObj(i -> Path.of("diagrams", "v" + i + ".svg"))
                .collect(ImmutableList.toImmutableList()));
    }

    @Test
    void theReport_saysHowManyFilesAndWhere() {
        String summary = report(21, 0, 12).summary();

        assertTrue(summary.contains("12 diagram(s) written"), summary);
        assertTrue(summary.contains("diagrams"), "The directory must be named: " + summary);
    }

    @Test
    void aFileWithoutDirectory_isNamedAfterItself() {
        String summary = new GenerationReport(5, ImmutableList.of(),
            ImmutableList.of(Path.of("diagram_architecture.svg"))).summary();

        assertFalse(summary.contains("null"), summary);
        assertTrue(summary.contains("diagram_architecture.svg"), summary);
    }

    @Test
    void aSingleFileInADirectory_isNamedToo() {
        String summary = new GenerationReport(5, ImmutableList.of(),
            ImmutableList.of(Path.of("docs", "archi.svg"))).summary();

        assertTrue(summary.contains(Path.of("docs", "archi.svg").toString()), summary);
    }

    @Test
    void aSingleFilePrefixedByDotSlash_isNamedToo() {
        String summary = new GenerationReport(5, ImmutableList.of(),
            ImmutableList.of(Path.of("./diagram.svg"))).summary();

        assertTrue(summary.contains("diagram.svg"), summary);
    }

    @Test
    void severalFiles_areSummarizedByTheirDirectory() {
        String summary = report(21, 0, 12).summary();

        assertTrue(summary.contains("diagrams"), summary);
        assertFalse(summary.contains("v0.svg"), "The views must not be listed: " + summary);
    }

    @Test
    void theReport_saysWhatWasIgnored() {
        String summary = report(21, 23, 12).summary();

        assertTrue(summary.contains("21 class(es) drawn"), summary);
        assertTrue(summary.contains("23 ignored"), summary);
    }

    @Test
    void fewIgnoredClasses_areNamed() {
        String summary = report(5, 2, 1).summary();

        assertTrue(summary.contains("Ignored0"), summary);
        assertTrue(summary.contains("Ignored1"), summary);
    }

    @Test
    void manyIgnoredClasses_areCountedWithoutBeingNamed() {
        String summary = report(5, 40, 1).summary();

        assertTrue(summary.contains("40 ignored"), summary);
        assertFalse(summary.contains("Ignored0"), summary);
    }

    @Test
    void noIgnoredClass_leavesNoList() {
        String summary = report(5, 0, 1).summary();

        assertTrue(summary.contains("0 ignored"), summary);
        assertFalse(summary.contains(" : "), summary);
    }
}
