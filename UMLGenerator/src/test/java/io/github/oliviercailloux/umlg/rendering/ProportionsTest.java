package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProportionsTest {

    private static Path svg(Path folder, String name, int width, int height)
        throws IOException {
        Path file = folder.resolve(name + ".svg");
        Files.writeString(file,
            "<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" height=\""
                + height + "px\" preserveAspectRatio=\"none\" style=\"width:" + width
                + "px;height:" + height + "px;\" viewBox=\"0 0 " + width + " " + height
                + "\" width=\"" + width + "px\"></svg>",
            StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void aVeryWideRibbon_isReported(@TempDir Path tmp) throws IOException {
        assertTrue(Proportions.check(svg(tmp, "ribbon", 3570, 289), "Representable"));
    }

    @Test
    void averyTallDrawing_isReportedToo(@TempDir Path tmp) throws IOException {
        assertTrue(Proportions.check(svg(tmp, "colonne", 300, 3000), "Colonne"));
    }

    @Test
    void aBalancedDrawing_saysNothing(@TempDir Path tmp) throws IOException {
        assertFalse(Proportions.check(svg(tmp, "mapping", 1157, 488), "Mapping"));
        assertFalse(Proportions.check(svg(tmp, "mnemonics", 1069, 497), "Mnemonics"));
    }

    @Test
    void aSmallImage_isNotReportedEvenWhenElongated(@TempDir Path tmp) throws IOException {
        assertFalse(Proportions.check(svg(tmp, "petite", 800, 100), "Petite"));
    }

    @Test
    void justUnderTheThreshold_saysNothing(@TempDir Path tmp) throws IOException {
        assertFalse(Proportions.check(svg(tmp, "limite", 2000, 520), "Limite"));
    }

    @Test
    void aPng_isMeasuredToo(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("ribbon.png");
        byte[] png = new byte[24];
        write(png, 16, 3570);
        write(png, 20, 289);
        Files.write(file, png);

        assertTrue(Proportions.check(file, "Ribbon"));
    }

    @Test
    void anUnknownFormat_triggersNothing(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("notes.txt");
        Files.writeString(file, "ceci n'is not one image", StandardCharsets.UTF_8);

        assertFalse(Proportions.check(file, "Notes"));
    }

    private static void write(byte[] bytes, int start, int value) {
        bytes[start] = (byte) (value >>> 24);
        bytes[start + 1] = (byte) (value >>> 16);
        bytes[start + 2] = (byte) (value >>> 8);
        bytes[start + 3] = (byte) value;
    }
}
