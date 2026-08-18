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

class PlantUmlWriterOutputTest {

    private static final byte[] HEADER_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @UMLClass(name = "Invoice", stereotype = "Entity", strategy = UMLVisibility.PRIVATE)
    static class Fixture {
        private int amount;

        public void emit() {}
    }

    private static UmlProject projectFixture() {
        return UmlProject.given(Set.of(EntityExtractor.extract(Fixture.class)));
    }

    @Test
    void declaredTitle_isDrawnInTheImage(@TempDir Path tmp) throws Exception {
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

    private static int widthPng(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        return ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16)
            | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
    }

    @Test
    void pngWiderThanThePlantUmlCeiling_isNotCropped(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("large.png");

        PlantUmlWriter.write(stringHorizontal(40), output.toString());

        assertTrue(widthPng(output) > 4096,
            "The PNG was cropped at PlantUML's ceiling: width = " + widthPng(output));
    }

    @Test
    void imageCeiling_isRestoredAfterUse(@TempDir Path tmp) throws Exception {
        int before = GraphvizUtils.getenvImageLimit();

        PlantUmlWriter.write(projectFixture(), tmp.resolve("diagram.svg").toString());

        assertEquals(before, GraphvizUtils.getenvImageLimit(),
            "The ceiling set for the rendering must be removed afterwards.");
    }

    @Test
    void uppercaseExtension_isRecognized(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("Diagram.SVG");

        PlantUmlWriter.write(projectFixture(), output.toString());

        assertTrue(Files.readString(output, StandardCharsets.UTF_8).contains("<svg"),
            "\".SVG\" must produce SVG, not PNG.");
    }

    @Test
    void unknownExtension_isRefused(@TempDir Path tmp) {
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
