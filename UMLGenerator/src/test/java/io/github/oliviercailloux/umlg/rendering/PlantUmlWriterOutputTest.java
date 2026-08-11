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

// Ce que contient réellement le fichier écrit : le bon format, le bon contenu, et pas une image
// d'erreur déguisée.
//
// C'est le complément indispensable de PlantUmlWriterTest : PlantUML dessine une image même
// quand il échoue, si bien qu'un fichier non vide ne prouve rien. Ici, on relit le contenu.
class PlantUmlWriterOutputTest {

    // Le nombre magique d'un fichier PNG : ses huit premiers octets. Sert à vérifier le format
    // réellement produit, sans se fier au nom du fichier.
    private static final byte[] ENTETE_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @UMLClass(name = "Facture", stereotype = "Entity", strategy = UMLVisibility.PRIVATE)
    static class Cobaye {
        private int montant;

        public void emettre() {}
    }

    // Le projet d'essai, refabriqué à chaque appel : aucun test n'hérite de l'état d'un autre.
    private static UmlProject projetCobaye() {
        return UmlProject.given(Set.of(EntityExtractor.extract(Cobaye.class)));
    }

    @Test
    void titreDeclare_estDessineDansLImage(@TempDir Path tmp) throws Exception {
        // Le maillon que ni le scan ni le rendu ne couvrent seuls : le writer doit passer au
        // rendu les réglages que porte le projet. Les oublier ne casserait rien — l'image
        // resterait valide, simplement sans le titre demandé.
        Path sortie = tmp.resolve("diagramme.svg");
        UmlProject projet = UmlProject.given(Set.of(EntityExtractor.extract(Cobaye.class)),
            new UmlDiagram("Architecture du module Facturation", UMLFlow.TOP_TO_BOTTOM, true));

        PlantUmlWriter.write(projet, sortie.toString());

        assertTrue(Files.readString(sortie, StandardCharsets.UTF_8)
            .contains("Architecture du module Facturation"),
            "Le titre déclaré doit se lire dans l'image livrée.");
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

    // Une chaîne de classes reliées vers la droite : le seul moyen sûr d'obtenir un diagramme
    // plus large que le plafond de PlantUML, qui range sinon les boîtes isolées en grille.
    private static UmlProject chaineHorizontale(int longueur) {
        Set<UmlEntity> entites = new LinkedHashSet<>();
        for (int i = 0; i < longueur; i++) {
            UmlEntity.Builder boite = UmlEntity.builder("com.exemple.Classe" + i, "Classe" + i);
            if (i + 1 < longueur) {
                boite.relations(List.of(
                    UmlAssociation.builder("suivant", "com.exemple.Classe" + (i + 1))
                        .direction(UMLDirection.RIGHT).build()));
            }
            entites.add(boite.build());
        }
        return UmlProject.given(entites);
    }

    // La largeur d'un PNG, lue dans son en-tête IHDR : quatre octets à partir du seizième.
    private static int largeurPng(Path fichier) throws Exception {
        byte[] octets = Files.readAllBytes(fichier);
        return ((octets[16] & 0xFF) << 24) | ((octets[17] & 0xFF) << 16)
            | ((octets[18] & 0xFF) << 8) | (octets[19] & 0xFF);
    }

    @Test
    void pngPlusLargeQueLePlafondDePlantUml_nEstPasRogne(@TempDir Path tmp) throws Exception {
        // PlantUML plafonne ses images à 4096 pixels et rogne au-delà, sans rien signaler : des
        // boîtes entières disparaissaient du PNG alors que le SVG, vectoriel, était complet.
        // Quarante classes en chaîne dépassent largement ce plafond.
        Path sortie = tmp.resolve("large.png");

        PlantUmlWriter.write(chaineHorizontale(40), sortie.toString());

        assertTrue(largeurPng(sortie) > 4096,
            "Le PNG a été rogné au plafond de PlantUML : largeur = " + largeurPng(sortie));
    }

    @Test
    void plafondDImage_estRenduAuFilApresUsage(@TempDir Path tmp) throws Exception {
        // Le réglage est une ThreadLocal de PlantUML : le poser sans le retirer laisserait la
        // bibliothèque modifier, en douce et durablement, le comportement de son appelant.
        int avant = GraphvizUtils.getenvImageLimit();

        PlantUmlWriter.write(projetCobaye(), tmp.resolve("diagramme.svg").toString());

        assertEquals(avant, GraphvizUtils.getenvImageLimit(),
            "Le plafond posé pour le rendu doit être retiré ensuite.");
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
    void extensionInconnue_estRefusee(@TempDir Path tmp) {
        // Le writer n'est pas protégé par le Builder quand on l'appelle directement, et il ne se
        // protégeait pas lui-même : writeViews refuse une extension inconnue, celui-ci non. Tout
        // ce qui n'était pas .svg passait pour du PNG, et un nom sans point du tout levait une
        // StringIndexOutOfBoundsException « Range [0, -1) » — indéchiffrable pour un appelant.
        IllegalArgumentException refus = assertThrows(IllegalArgumentException.class,
            () -> PlantUmlWriter.write(projetCobaye(), tmp.resolve("diagramme.txt").toString()));

        assertTrue(refus.getMessage().contains(".png"), refus.getMessage());
        assertTrue(refus.getMessage().contains(".svg"), refus.getMessage());
        assertTrue(refus.getMessage().contains("diagramme.txt"),
            "Le message doit nommer le fichier fautif : " + refus.getMessage());
    }

    @Test
    void nomSansExtension_estRefuseSansExceptionObscure(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class,
            () -> PlantUmlWriter.write(projetCobaye(), tmp.resolve("diagramme").toString()));
    }

    @Test
    void ecritureRefusee_neLaisseRienDerriereElle(@TempDir Path tmp) throws Exception {
        // Le flux de sortie était ouvert avant toute vérification : le fichier existait déjà,
        // vide, quand la panne survenait. Un diagramme de zéro octet ne se distingue d'un
        // diagramme correct qu'en l'ouvrant — c'est le pire des résultats.
        Path sortie = tmp.resolve("diagramme.txt");

        assertThrows(IllegalArgumentException.class,
            () -> PlantUmlWriter.write(projetCobaye(), sortie.toString()));

        assertFalse(Files.exists(sortie), "Aucun fichier ne doit rester : " + sortie);
        assertFalse(Files.exists(tmp.resolve("diagramme.puml")), "Ni la source PlantUML.");
        try (var contenu = Files.list(tmp)) {
            assertEquals(List.of(), contenu.toList(), "Le dossier doit être resté vide.");
        }
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
        //
        // L'exception nommée, et non « Exception » : la classe mère accepterait aussi bien un
        // NullPointerException venu d'ailleurs, et le test ne prouverait plus rien du dossier
        // manquant.
        Path sortie = tmp.resolve("sous/dossier/absent/diagramme.svg");

        assertThrows(IOException.class,
            () -> PlantUmlWriter.write(projetCobaye(), sortie.toString()));
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
