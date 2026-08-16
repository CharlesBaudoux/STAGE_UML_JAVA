package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The proportion check: which images are reported, and above all which are not
// not.
//
// The second half counts as much as the first. A warning firing on a
// a readable drawing teaches one to stop reading them — and this one addresses a user who has
// wrong, since its diagram is right.
class ProportionsTest {

    // An SVG reduced to its header: the check needs the dimensions only, and making them
    // avoids running the rendering engine to exercise a rule of three.
    //
    // The field order imitates the engine's — height before width, in alphabetical order
    // — and that detail is not one: a fake file writing them in the order expected by
    // the code would bless a read that fails on every real file. That has happened.
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
        // The measured case that motivated this check: 3570 by 289, that is 12 to 1.
        assertTrue(Proportions.check(svg(tmp, "ribbon", 3570, 289), "Representable"));
    }

    @Test
    void averyTallDrawing_isReportedToo(@TempDir Path tmp) throws IOException {
        // Imbalance is judged in both directions: a column three thousand pixels high
        // is no more readable than a ribbon three thousand wide.
        assertTrue(Proportions.check(svg(tmp, "colonne", 300, 3000), "Colonne"));
    }

    @Test
    void aBalancedDrawing_saysNothing(@TempDir Path tmp) throws IOException {
        // Proportions measured on the well-composed views of a real project.
        assertFalse(Proportions.check(svg(tmp, "mapping", 1157, 488), "Mapping"));
        assertFalse(Proportions.check(svg(tmp, "mnemonics", 1069, 497), "Mnemonics"));
    }

    @Test
    void aSmallImage_isNotReportedEvenWhenElongated(@TempDir Path tmp) throws IOException {
        // An image of a few hundred pixels stays readable even much elongated: the
        // reporting would be a false complaint, and that is what the size threshold rules out.
        assertFalse(Proportions.check(svg(tmp, "petite", 800, 100), "Petite"));
    }

    @Test
    void justUnderTheThreshold_saysNothing(@TempDir Path tmp) throws IOException {
        // 3.9 to 1 on a large image: unbalanced, but not enough to interfere.
        assertFalse(Proportions.check(svg(tmp, "limite", 2000, 520), "Limite"));
    }

    @Test
    void aPng_isMeasuredToo(@TempDir Path tmp) throws IOException {
        // The dimensions of a PNG are read in its IHDR block, and not in a textual header:
        // the check must know both formats the library writes.
        Path file = tmp.resolve("ribbon.png");
        byte[] png = new byte[24];
        // The signature, length and type of the block matter little here: only the two integers count
        // big-endian ones that follow, at bytes 16 and 20.
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
