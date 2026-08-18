package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlantUmlWriterTest {

    @UMLClass
    static class ClassFixture {}

    @Test
    void testGenerationFileSvg(@TempDir Path temporaryFolder) throws Exception {
        UmlEntity fakeEntity = EntityExtractor.extract(ClassFixture.class);
        UmlProject project = UmlProject.given(Set.of(fakeEntity));

        Path pathFileOutput = temporaryFolder.resolve("diagram_test.svg");
        PlantUmlWriter.write(project, pathFileOutput.toString());

        assertTrue(Files.exists(pathFileOutput));
        assertTrue(Files.size(pathFileOutput) > 0);
    }

    @Test
    void testGenerationFilePng(@TempDir Path temporaryFolder) throws Exception {
        UmlEntity fakeEntity = EntityExtractor.extract(ClassFixture.class);
        UmlProject project = UmlProject.given(Set.of(fakeEntity));

        Path pathFileOutput = temporaryFolder.resolve("diagram_test.png");
        PlantUmlWriter.write(project, pathFileOutput.toString());

        assertTrue(Files.exists(pathFileOutput));
        assertTrue(Files.size(pathFileOutput) > 0);
    }

    @Test
    void aSingleRibbonShapedFile_isReportedToo(@TempDir Path temporaryFolder)
        throws Exception {
        Set<UmlEntity> boxes = new LinkedHashSet<>();
        for (int i = 0; i < 14; i++) {
            String name = "UneClasseAuNomDeliberementTresLongNumero" + i;
            boxes.add(UmlEntity.builder("com.example." + name, name).build());
        }
        Path file = temporaryFolder.resolve("ribbon.svg");

        PrintStream before = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            PlantUmlWriter.write(UmlProject.given(boxes), file.toString());
        } finally {
            System.setErr(before);
        }
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("hard to read"),
            "The ribbon must be reported: " + buffer.toString(StandardCharsets.UTF_8));
    }

    @Test
    void testFailsFastOnNullArguments() {
        assertThrows(NullPointerException.class, () -> {
            PlantUmlWriter.write(null, "output.svg");
        });

        assertThrows(NullPointerException.class, () -> {
            UmlEntity fakeEntity = EntityExtractor.extract(ClassFixture.class);
            UmlProject validProject = UmlProject.given(Set.of(fakeEntity));
            PlantUmlWriter.write(validProject, null);
        });
    }
}