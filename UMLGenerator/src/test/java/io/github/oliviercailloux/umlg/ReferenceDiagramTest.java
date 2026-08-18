package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReferenceDiagramTest {

    private static final String REFERENCE_FILE = "/reference-diagram.puml";

    private static final Rules RULES = Rules.read("""
        { "hide": ["get*", "values", "valueOf"] }
        """, "the rules of the reference project");

    private static ImmutableList<UmlEntity> entities() {
        return ImmutableList.of(
            EntityExtractor.extract(ReferenceProject.Identifiable.class, RULES),
            EntityExtractor.extract(ReferenceProject.Person.class, RULES),
            EntityExtractor.extract(ReferenceProject.Status.class, RULES),
            EntityExtractor.extract(ReferenceProject.Order.class, RULES),
            EntityExtractor.extract(ReferenceProject.ServiceBank.class, RULES),
            EntityExtractor.extract(ReferenceProject.Settlement.class, RULES),
            EntityExtractor.extract(ReferenceProject.ClientInvoice.class, RULES));
    }

    private static String documentGenerated() {
        return PlantUmlAssert.documentFor(entities().toArray(new UmlEntity[0]));
    }

    private static String reference() throws Exception {
        try (InputStream stream = ReferenceDiagramTest.class
            .getResourceAsStream(REFERENCE_FILE)) {

            assertNotNull(stream, "Reference file not found: " + REFERENCE_FILE);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theGeneratedDocument_isTheOneWrittenByHand() throws Exception {
        assertEquals(reference(), documentGenerated());
    }

    @Test
    void theGeneratedDocument_isAcceptableToPlantUml() {
        PlantUmlAssert.assertCompile(documentGenerated());
    }

    @Test
    void theGeneratedDocument_formsASingleDiagram() {
        assertEquals(1, PlantUmlAssert.diagramsCount(documentGenerated()));
    }
}
