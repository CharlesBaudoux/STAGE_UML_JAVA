package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DiagrammeDeReferenceTest {

    private static final String FICHIER_DE_REFERENCE = "/diagramme-de-reference.puml";

    private static final Regles REGLES = Regles.lues("""
        { "hide": ["get*", "values", "valueOf"] }
        """, "les règles du projet de référence");

    private static ImmutableList<UmlEntity> entites() {
        return ImmutableList.of(
            EntityExtractor.extract(ProjetDeReference.Identifiable.class, REGLES),
            EntityExtractor.extract(ProjetDeReference.Personne.class, REGLES),
            EntityExtractor.extract(ProjetDeReference.Statut.class, REGLES),
            EntityExtractor.extract(ProjetDeReference.Commande.class, REGLES),
            EntityExtractor.extract(ProjetDeReference.ServiceBancaire.class, REGLES),
            EntityExtractor.extract(ProjetDeReference.ClientFacture.class, REGLES));
    }

    private static String documentEngendre() {
        return PlantUmlAssert.documentPour(entites().toArray(new UmlEntity[0]));
    }

    private static String reference() throws Exception {
        try (InputStream flux = DiagrammeDeReferenceTest.class
            .getResourceAsStream(FICHIER_DE_REFERENCE)) {

            assertNotNull(flux, "Fichier de référence introuvable : " + FICHIER_DE_REFERENCE);
            return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void leDocumentEngendre_estCeluiEcritALaMain() throws Exception {
        assertEquals(reference(), documentEngendre());
    }

    @Test
    void leDocumentEngendre_estAcceptibleParPlantUml() {
        PlantUmlAssert.assertCompile(documentEngendre());
    }

    @Test
    void leDocumentEngendre_neFormeQuunSeulDiagramme() {
        assertEquals(1, PlantUmlAssert.nombreDeDiagrammes(documentEngendre()));
    }
}
