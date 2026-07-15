package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlProject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests du fichier réellement écrit par {@link PlantUmlWriter}.
 *
 * <p>Vérifier {@code Files.exists()} et {@code size > 0} ne prouve rien : PlantUML écrit
 * une image parfaitement valide même quand la syntaxe est fausse — elle affiche
 * simplement « syntax error » en rouge. Ces tests regardent donc le contenu : le bon
 * format de fichier, et la présence des classes et membres attendus.
 */
class PlantUmlWriterOutputTest {

    private static final byte[] ENTETE_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @UMLClass(name = "Facture", stereotype = "Entity", strategy = UMLVisibility.PRIVATE)
    static class Cobaye {
        private int montant;

        public void emettre() {}
    }

    private static UmlProject projetCobaye() {
        return UmlProject.given(Set.of(EntityExtractor.extract(Cobaye.class)));
    }

    @Test
    void svgProduit_contientLesClassesEtLeursMembres(@TempDir Path tmp) throws Exception {
        // Le vrai contrat de la bibliothèque : ce que le développeur a annoté se retrouve
        // dans l'image livrée.
        Path sortie = tmp.resolve("diagramme.svg");

        PlantUmlWriter.write(projetCobaye(), sortie.toString());
        String svg = Files.readString(sortie, StandardCharsets.UTF_8);

        assertTrue(svg.contains("Facture"), "Le nom de la classe doit être dessiné.");
        assertTrue(svg.contains("montant"), "L'attribut doit être dessiné.");
        assertTrue(svg.contains("emettre"), "La méthode métier doit être dessinée.");
        assertTrue(svg.contains("Entity"), "Le stéréotype doit être dessiné.");
    }

    @Test
    void svgProduit_nEstPasUneImageDErreur(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("diagramme.svg");

        PlantUmlWriter.write(projetCobaye(), sortie.toString());
        String svg = Files.readString(sortie, StandardCharsets.UTF_8);

        assertFalse(svg.toLowerCase().contains("syntax error"),
            "PlantUML a rendu une image d'erreur au lieu du diagramme :\n" + svg);
    }

    @Test
    void extensionSvg_produitBienDuSvg(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("diagramme.svg");

        PlantUmlWriter.write(projetCobaye(), sortie.toString());

        assertTrue(Files.readString(sortie, StandardCharsets.UTF_8).contains("<svg"),
            "Un fichier .svg doit contenir un élément <svg>, pas des octets binaires.");
    }

    @Test
    void extensionPng_produitBienDuPng(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("diagramme.png");

        PlantUmlWriter.write(projetCobaye(), sortie.toString());

        byte[] entete = Arrays.copyOf(Files.readAllBytes(sortie), ENTETE_PNG.length);
        assertArrayEquals(ENTETE_PNG, entete, "Les octets d'en-tête PNG sont attendus.");
    }

    @Test
    void extensionEnMajuscules_estReconnue(@TempDir Path tmp) throws Exception {
        // Le writer normalise la casse avant de choisir le format, et UMLGenerator.Builder
        // s'appuie désormais sur supporte() plutôt que sur sa propre comparaison : les deux
        // acceptent le même ensemble de noms (cf. UMLGeneratorIntegrationTest).
        Path sortie = tmp.resolve("Diagramme.SVG");

        PlantUmlWriter.write(projetCobaye(), sortie.toString());

        assertTrue(Files.readString(sortie, StandardCharsets.UTF_8).contains("<svg"),
            "« .SVG » doit produire du SVG, pas du PNG.");
    }

    @Test
    void extensionInconnue_bascculeEnPng(@TempDir Path tmp) throws Exception {
        // Caractérisation : le writer n'est pas protégé par le Builder quand on l'appelle
        // directement ; tout ce qui n'est pas .svg est traité comme du PNG.
        Path sortie = tmp.resolve("diagramme.txt");

        PlantUmlWriter.write(projetCobaye(), sortie.toString());

        byte[] entete = Arrays.copyOf(Files.readAllBytes(sortie), ENTETE_PNG.length);
        assertArrayEquals(ENTETE_PNG, entete);
    }

    @Test
    void fichierExistant_estEcrase(@TempDir Path tmp) throws Exception {
        Path sortie = tmp.resolve("diagramme.svg");
        Files.writeString(sortie, "contenu précédent qui doit disparaître");

        PlantUmlWriter.write(projetCobaye(), sortie.toString());

        String svg = Files.readString(sortie, StandardCharsets.UTF_8);
        assertFalse(svg.contains("contenu précédent"), "L'ancien contenu doit être remplacé.");
        assertTrue(svg.contains("Facture"));
    }

    @Test
    void dossierParentInexistant_echoue(@TempDir Path tmp) {
        // Le writer ne crée pas l'arborescence : autant que l'échec soit franc.
        Path sortie = tmp.resolve("sous/dossier/absent/diagramme.svg");

        assertThrows(Exception.class, () -> PlantUmlWriter.write(projetCobaye(), sortie.toString()));
    }

    @Test
    void plusieursClasses_apparaissentToutesDansLeSvg(@TempDir Path tmp) throws Exception {
        UmlProject projet = UmlProject.given(Set.of(
            EntityExtractor.extract(Cobaye.class),
            EntityExtractor.extract(AutreCobaye.class)));
        Path sortie = tmp.resolve("diagramme.svg");

        PlantUmlWriter.write(projet, sortie.toString());
        String svg = Files.readString(sortie, StandardCharsets.UTF_8);

        assertTrue(svg.contains("Facture"), svg);
        assertTrue(svg.contains("Client"), svg);
        assertFalse(svg.toLowerCase().contains("syntax error"), svg);
    }

    @UMLClass(name = "Client")
    static class AutreCobaye {}

    @Test
    void argumentsNuls_sontRefuses() {
        assertThrows(NullPointerException.class, () -> PlantUmlWriter.write(null, "sortie.svg"));
        assertThrows(NullPointerException.class, () -> PlantUmlWriter.write(projetCobaye(), null));
    }
}
