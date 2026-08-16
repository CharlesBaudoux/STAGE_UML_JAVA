package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

// The report returned at the end of the generation.
//
// It replaced a "Success" line per image, which was right only back when one wrote
// wrote only one: twelve views gave twelve nearly identical lines, and the only information
// useful was lost in it.
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
        // The default single-file case, "diagram_architecture.svg": a path of a
        // single segment has no parent, and the report wrote "written to null" — the
        // the very first message a user having configured nothing would see.
        String summary = new GenerationReport(5, ImmutableList.of(),
            ImmutableList.of(Path.of("diagram_architecture.svg"))).summary();

        assertFalse(summary.contains("null"), summary);
        assertTrue(summary.contains("diagram_architecture.svg"), summary);
    }

    @Test
    void aSingleFileInADirectory_isNamedToo() {
        // The returned report named the directory whatever the file count: "written to
        // docs" for "docs/archi.svg". It therefore silenced the name the user had just
        // give, and did not say where to look.
        String summary = new GenerationReport(5, ImmutableList.of(),
            ImmutableList.of(Path.of("docs", "archi.svg"))).summary();

        assertTrue(summary.contains(Path.of("docs", "archi.svg").toString()), summary);
    }

    @Test
    void aSingleFilePrefixedByDotSlash_isNamedToo() {
        // The variant that gave "written to ." — the worst case, since the message
        // meant the whole current directory.
        String summary = new GenerationReport(5, ImmutableList.of(),
            ImmutableList.of(Path.of("./diagram.svg"))).summary();

        assertTrue(summary.contains("diagram.svg"), summary);
    }

    @Test
    void severalFiles_areSummarizedByTheirDirectory() {
        // The other half of the rule: twenty views are not listed, their directory is enough.
        String summary = report(21, 0, 12).summary();

        assertTrue(summary.contains("diagrams"), summary);
        assertFalse(summary.contains("v0.svg"), "The views must not be listed: " + summary);
    }

    @Test
    void theReport_saysWhatWasIgnored() {
        // The information nobody had: a class without @UMLClass is out of the diagram,
        // and nothing told the one dropped on purpose from the one forgotten.
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
        // Beyond a dozen, the list would drown the rest; the count is enough to know there
        // must go and look.
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
