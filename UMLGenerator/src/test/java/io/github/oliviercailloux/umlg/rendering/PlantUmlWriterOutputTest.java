package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.sourceforge.plantuml.dot.GraphvizUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// What the written file really holds: the right format, the right content, and not an
// error in disguise.
//
// It is the indispensable counterpart of PlantUmlWriterTest: PlantUML draws an image even
// when it fails, so that a non-empty file proves nothing. Here, the content is read back.
class PlantUmlWriterOutputTest {

    // The magic number of a PNG file: its first eight bytes. Used to check the format
    // actually produced, without trusting the file name.
    private static final byte[] HEADER_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @UMLClass(name = "Invoice", stereotype = "Entity", strategy = UMLVisibility.PRIVATE)
    static class Fixture {
        private int amount;

        public void emit() {}
    }

    // The trial project, rebuilt on each call: no test inherits the state of another.
    private static UmlProject projectFixture() {
        return UmlProject.given(Set.of(EntityExtractor.extract(Fixture.class)));
    }

    @Test
    void declaredTitle_isDrawnInTheImage(@TempDir Path tmp) throws Exception {
        // The link neither the scan nor the rendering covers on its own: the writer must pass to the
        // render the settings the project carries. Forgetting them would break nothing — the image
        // would stay valid, simply without the requested title.
        Path output = tmp.resolve("diagram.svg");
        UmlProject project = UmlProject.given(Set.of(EntityExtractor.extract(Fixture.class)),
            new UmlDiagram("Architecture of the Billing module", UMLFlow.TOP_TO_BOTTOM, true));

        PlantUmlWriter.write(project, output.toString());

        assertTrue(Files.readString(output, StandardCharsets.UTF_8)
            .contains("Architecture of the Billing module"),
            "The declared title must be readable in the delivered image.");
    }

    @Test
    void producedSvg_containsTheClassesAndTheirMembers(@TempDir Path tmp) throws Exception {
        // The real contract of the library: what the developer annotated is found again
        // in the delivered image.
        Path output = tmp.resolve("diagram.svg");

        PlantUmlWriter.write(projectFixture(), output.toString());
        String svg = Files.readString(output, StandardCharsets.UTF_8);

        assertTrue(svg.contains("Invoice"), "The class name must be drawn.");
        assertTrue(svg.contains("amount"), "The field must be drawn.");
        assertTrue(svg.contains("emit"), "The business method must be drawn.");
        assertTrue(svg.contains("Entity"), "The stereotype must be drawn.");
    }

    @Test
    void producedSvg_isNotAnErrorImage(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("diagram.svg");

        PlantUmlWriter.write(projectFixture(), output.toString());
        String svg = Files.readString(output, StandardCharsets.UTF_8);

        assertFalse(svg.toLowerCase().contains("syntax error"),
            "PlantUML rendered an error image instead of the diagram:\n" + svg);
    }

    @Test
    void svgExtension_reallyProducesSvg(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("diagram.svg");

        PlantUmlWriter.write(projectFixture(), output.toString());

        assertTrue(Files.readString(output, StandardCharsets.UTF_8).contains("<svg"),
            "An .svg file must hold an <svg> element, not binary bytes.");
    }

    @Test
    void pngExtension_reallyProducesPng(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("diagram.png");

        PlantUmlWriter.write(projectFixture(), output.toString());

        byte[] header = Arrays.copyOf(Files.readAllBytes(output), HEADER_PNG.length);
        assertArrayEquals(HEADER_PNG, header, "The PNG header bytes are expected.");
    }

    // A chain of classes linked to the right: the only sure way of getting a diagram
    // wider than PlantUML's ceiling, which otherwise files lone boxes in a grid.
    private static UmlProject stringHorizontal(int length) {
        Set<UmlEntity> entities = new LinkedHashSet<>();
        for (int i = 0; i < length; i++) {
            UmlEntity.Builder box = UmlEntity.builder("com.example.Item" + i, "Item" + i);
            if (i + 1 < length) {
                box.relations(List.of(
                    UmlAssociation.builder("next", "com.example.Item" + (i + 1))
                        .direction(UMLDirection.RIGHT).build()));
            }
            entities.add(box.build());
        }
        return UmlProject.given(entities);
    }

    // The width of a PNG, read in its IHDR header: four bytes from the sixteenth on.
    private static int widthPng(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        return ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16)
            | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
    }

    @Test
    void pngWiderThanThePlantUmlCeiling_isNotCropped(@TempDir Path tmp) throws Exception {
        // PlantUML caps its images at 4096 pixels and crops beyond, reporting nothing:
        // whole boxes vanished from the PNG while the SVG, being vector, was complete.
        // Forty chained classes go well beyond that ceiling.
        Path output = tmp.resolve("large.png");

        PlantUmlWriter.write(stringHorizontal(40), output.toString());

        assertTrue(widthPng(output) > 4096,
            "The PNG was cropped at PlantUML's ceiling: width = " + widthPng(output));
    }

    @Test
    void imageCeiling_isRestoredAfterUse(@TempDir Path tmp) throws Exception {
        // The setting is a PlantUML ThreadLocal: setting it without removing it would leave the
        // library change, quietly and durably, the behaviour of its caller.
        int before = GraphvizUtils.getenvImageLimit();

        PlantUmlWriter.write(projectFixture(), tmp.resolve("diagram.svg").toString());

        assertEquals(before, GraphvizUtils.getenvImageLimit(),
            "The ceiling set for the rendering must be removed afterwards.");
    }

    @Test
    void uppercaseExtension_isRecognized(@TempDir Path tmp) throws Exception {
        // The writer normalizes the case before choosing the format, and UMLGenerator.Builder
        // now relies on supports() rather than on its own comparison: the two
        // accept the same set of names (see UMLGeneratorIntegrationTest).
        Path output = tmp.resolve("Diagram.SVG");

        PlantUmlWriter.write(projectFixture(), output.toString());

        assertTrue(Files.readString(output, StandardCharsets.UTF_8).contains("<svg"),
            "\".SVG\" must produce SVG, not PNG.");
    }

    @Test
    void unknownExtension_isRefused(@TempDir Path tmp) {
        // The writer is not protected by the Builder when called directly, and it does not
        // did not protect itself: writeViews refuses an unknown extension, this one does not. Everything
        // anything that was not .svg passed for PNG, and a name with no dot at all raised an
        // a StringIndexOutOfBoundsException "Range [0, -1)" — undecipherable for a caller.
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
            () -> PlantUmlWriter.write(projectFixture(), tmp.resolve("diagram.txt").toString()));

        assertTrue(refusal.getMessage().contains(".png"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(".svg"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("diagram.txt"),
            "The message must name the faulty file: " + refusal.getMessage());
    }

    @Test
    void nameWithoutExtension_isRefusedWithoutObscureException(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class,
            () -> PlantUmlWriter.write(projectFixture(), tmp.resolve("diagram").toString()));
    }

    @Test
    void refusedWrite_leavesNothingBehind(@TempDir Path tmp) throws Exception {
        // The output stream was opened before any check: the file already existed,
        // empty, when the failure happened. A zero-byte diagram is told from a
        // a correct diagram only by opening it — that is the worst of outcomes.
        Path output = tmp.resolve("diagram.txt");

        assertThrows(IllegalArgumentException.class,
            () -> PlantUmlWriter.write(projectFixture(), output.toString()));

        assertFalse(Files.exists(output), "No file must remain: " + output);
        assertFalse(Files.exists(tmp.resolve("diagram.puml")), "Nor the PlantUML source.");
        try (var content = Files.list(tmp)) {
            assertEquals(List.of(), content.toList(), "The directory must have stayed empty.");
        }
    }

    @Test
    void existingFile_isOverwritten(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("diagram.svg");
        Files.writeString(output, "previous content that must vanish");

        PlantUmlWriter.write(projectFixture(), output.toString());

        String svg = Files.readString(output, StandardCharsets.UTF_8);
        assertFalse(svg.contains("previous content"), "The former content must be replaced.");
        assertTrue(svg.contains("Invoice"));
    }

    @Test
    void missingParentDirectory_fails(@TempDir Path tmp) {
        // The writer does not create the tree: better that the failure be plain.
        //
        // The named exception, and not "Exception": the parent class would just as well accept a
        // a NullPointerException coming from elsewhere, and the test would prove nothing about the directory
        // manquant.
        Path output = tmp.resolve("under/folder/missing/diagram.svg");

        assertThrows(IOException.class,
            () -> PlantUmlWriter.write(projectFixture(), output.toString()));
    }

    @Test
    void severalClasses_allAppearInTheSvg(@TempDir Path tmp) throws Exception {
        UmlProject project = UmlProject.given(Set.of(
            EntityExtractor.extract(Fixture.class),
            EntityExtractor.extract(OtherFixture.class)));
        Path output = tmp.resolve("diagram.svg");

        PlantUmlWriter.write(project, output.toString());
        String svg = Files.readString(output, StandardCharsets.UTF_8);

        assertTrue(svg.contains("Invoice"), svg);
        assertTrue(svg.contains("Client"), svg);
        assertFalse(svg.toLowerCase().contains("syntax error"), svg);
    }

    @UMLClass(name = "Client")
    static class OtherFixture {}

    @Test
    void nullArguments_areRefused() {
        assertThrows(NullPointerException.class, () -> PlantUmlWriter.write(null, "output.svg"));
        assertThrows(NullPointerException.class, () -> PlantUmlWriter.write(projectFixture(), null));
    }
}
