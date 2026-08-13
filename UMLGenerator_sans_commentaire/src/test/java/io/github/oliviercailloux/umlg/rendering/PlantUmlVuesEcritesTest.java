package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlProject;
import io.github.oliviercailloux.umlg.model.UmlView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlantUmlVuesEcritesTest {

    private static UmlEntity boite(String nom) {
        return UmlEntity.builder("com.exemple." + nom, nom).build();
    }

    private static UmlProject projet() {
        ImmutableList<UmlEntity> toutes = ImmutableList.of(boite("Facture"), boite("Client"));

        return UmlProject.given(Set.copyOf(toutes), UmlDiagram.defaut(), List.of(
            new UmlView("Projet", toutes, UMLFlow.TOP_TO_BOTTOM),
            new UmlView("Facturation", ImmutableList.of(boite("Facture")), UMLFlow.TOP_TO_BOTTOM),
            new UmlView("Clients", ImmutableList.of(boite("Client")), UMLFlow.LEFT_TO_RIGHT)));
    }

    private static List<String> fichiers(Path dossier) throws IOException {
        try (Stream<Path> flux = Files.list(dossier)) {
            return flux.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }


    private static Path dossier(Path tmp) {
        return tmp.resolve("diagrammes");
    }

    private static ImmutableList<Path> ecrire(Path tmp) throws IOException {
        return ecrire(projet(), tmp, ".svg", false);
    }

    private static ImmutableList<Path> ecrire(UmlProject projet, Path tmp) throws IOException {
        return ecrire(projet, tmp, ".svg", false);
    }

    private static ImmutableList<Path> ecrire(UmlProject projet, Path tmp, String extension,
        boolean garderLaPrecedente) throws IOException {
        return PlantUmlWriter.writeViews(projet, dossier(tmp), extension, UmlStyle.PLANTUML,
            garderLaPrecedente);
    }

    @Test
    void lIntegraleEstALaRacine_lesAutresDansUnSousDossier(@TempDir Path tmp) throws Exception {
        Path dossier = dossier(tmp);

        ImmutableList<Path> ecrits = ecrire(tmp);

        assertEquals(3, ecrits.size(), ecrits.toString());
        assertEquals(List.of("projet.puml", "projet.svg", "vues"), fichiers(dossier));
        assertEquals(List.of("clients.puml", "clients.svg", "facturation.puml",
            "facturation.svg"), fichiers(dossier.resolve("vues")));
    }

    @Test
    void chaqueFichier_contientSaSeuleVue(@TempDir Path tmp) throws Exception {
        Path dossier = dossier(tmp);
        ecrire(tmp);

        String facturation =
            Files.readString(dossier.resolve("vues/facturation.svg"), StandardCharsets.UTF_8);

        assertTrue(facturation.contains("Facture"), "La classe retenue doit être dessinée.");
        assertFalse(facturation.contains("Client"), "Celle que la vue écarte ne doit pas l'être.");
    }

    @Test
    void chaqueVue_porteSonTitre(@TempDir Path tmp) throws Exception {
        Path dossier = dossier(tmp);
        ecrire(tmp);

        assertTrue(Files.readString(dossier.resolve("vues/clients.svg"), StandardCharsets.UTF_8)
            .contains("Clients"), "Le nom de la vue lui sert de titre.");
    }

    @Test
    void aucuneImageDErreur(@TempDir Path tmp) throws Exception {
        Path dossier = dossier(tmp);
        ecrire(tmp);

        for (Path fichier : List.of(dossier.resolve("projet.svg"),
            dossier.resolve("vues/facturation.svg"))) {
            assertFalse(
                Files.readString(fichier, StandardCharsets.UTF_8).toLowerCase()
                    .contains("syntax error"),
                "PlantUML a rendu une image d'erreur : " + fichier);
        }
    }

    @Test
    void lIntegraleGardeSonNomDeFichier_memeAvecUnTitre(@TempDir Path tmp) throws Exception {
        ImmutableList<UmlEntity> toutes = ImmutableList.of(boite("Facture"));
        UmlProject projet = UmlProject.given(Set.copyOf(toutes), UmlDiagram.defaut(),
            List.of(new UmlView("Architecture du module Facturation", toutes,
                UMLFlow.TOP_TO_BOTTOM)));
        Path dossier = dossier(tmp);

        ecrire(projet, tmp);

        assertTrue(Files.exists(dossier.resolve("projet.svg")), fichiers(dossier).toString());
        assertTrue(Files.readString(dossier.resolve("projet.svg"), StandardCharsets.UTF_8)
            .contains("Architecture du module Facturation"), "Le titre, lui, est bien écrit.");
    }

    @Test
    void formatPng_estAccepte(@TempDir Path tmp) throws Exception {
        ecrire(projet(), tmp, ".png", false);

        assertEquals(List.of("projet.png", "projet.puml", "vues"), fichiers(dossier(tmp)));
    }


    @Test
    void depuisLIntegrale_chaqueBoiteMeneAsaVue(@TempDir Path tmp) throws Exception {
        Path dossier = dossier(tmp);
        ecrire(tmp);

        String integrale =
            Files.readString(dossier.resolve("projet.svg"), StandardCharsets.UTF_8);

        assertTrue(integrale.contains("vues/facturation.svg"), "Le lien descend au sous-dossier.");
        assertTrue(integrale.contains("vues/clients.svg"), integrale.substring(0, 400));
    }

    @Test
    void entreDeuxVues_leCheminEstCeluiDuVoisin(@TempDir Path tmp) throws Exception {
        Path dossier = dossier(tmp);
        ecrire(tmp);

        String clients =
            Files.readString(dossier.resolve("vues/clients.svg"), StandardCharsets.UTF_8);

        assertFalse(clients.contains("vues/vues/"), "Aucun chemin ne se dédouble.");
    }

    @Test
    void uneVue_neRenvoiePasVersElleMeme(@TempDir Path tmp) throws Exception {
        Path dossier = dossier(tmp);
        ecrire(tmp);

        String facturation =
            Files.readString(dossier.resolve("vues/facturation.svg"), StandardCharsets.UTF_8);

        assertFalse(facturation.contains("facturation.svg"), facturation.substring(0, 400));
    }

    @Test
    void uneBoiteOrneeEtCliquable_estAccepteeParLeMoteur(@TempDir Path tmp) throws Exception {
        UmlEntity ornee = UmlEntity.builder("com.exemple.Animal", "Animal")
            .stereotype("entity").color("#EEEEEE").build();
        ImmutableList<UmlEntity> toutes = ImmutableList.of(boite("Facture"), ornee);

        UmlProject projet = UmlProject.given(Set.copyOf(toutes), UmlDiagram.defaut(), List.of(
            new UmlView("Projet", toutes, UMLFlow.TOP_TO_BOTTOM),
            new UmlView("Le vivant", ImmutableList.of(ornee), UMLFlow.TOP_TO_BOTTOM)));

        ecrire(projet, tmp);

        String integrale =
            Files.readString(dossier(tmp).resolve("projet.svg"), StandardCharsets.UTF_8);

        assertTrue(integrale.contains("vues/le-vivant.svg"), "Le lien est bien posé.");
        assertTrue(integrale.contains("entity"), "Et le stéréotype reste écrit.");
        assertFalse(integrale.toLowerCase().contains("syntax error"), "Aucune image d'erreur.");
    }


    @Test
    void deuxVuesDeMemeNomDeFichier_sontRefusees(@TempDir Path tmp) {
        ImmutableList<UmlEntity> toutes = ImmutableList.of(boite("Facture"), boite("Client"));
        UmlProject projet = UmlProject.given(Set.copyOf(toutes), UmlDiagram.defaut(), List.of(
            new UmlView("Projet", toutes, UMLFlow.TOP_TO_BOTTOM),
            new UmlView("Modèle", ImmutableList.of(boite("Facture")), UMLFlow.TOP_TO_BOTTOM),
            new UmlView("Modele", ImmutableList.of(boite("Client")), UMLFlow.TOP_TO_BOTTOM)));

        IllegalArgumentException refus = assertThrows(IllegalArgumentException.class,
            () -> ecrire(projet, tmp));

        assertTrue(refus.getMessage().contains("Modèle"), refus.getMessage());
        assertTrue(refus.getMessage().contains("Modele"),
            "Le message doit nommer les deux vues : " + refus.getMessage());
        assertTrue(refus.getMessage().contains("modele.svg"),
            "Et le fichier qu'elles se disputent : " + refus.getMessage());
    }

    @Test
    void deuxVuesDeNomsDistincts_sEcriventToutesLesDeux(@TempDir Path tmp) throws Exception {
        ecrire(tmp);

        assertEquals(List.of("clients.puml", "clients.svg", "facturation.puml",
            "facturation.svg"), fichiers(dossier(tmp).resolve("vues")));
    }


    @Test
    void replace_effaceLesDiagrammesDevenusInutiles(@TempDir Path tmp) throws Exception {
        Path dossier = dossier(tmp);
        Files.createDirectories(dossier.resolve("vues"));
        Path fantome = dossier.resolve("vues/ancienne-vue.svg");
        Files.writeString(fantome, "<svg/>", StandardCharsets.UTF_8);

        ecrire(tmp);

        assertFalse(Files.exists(fantome),
            "Une image que plus rien ne met à jour ment d'autant mieux qu'elle est à sa place.");
    }

    @Test
    void replace_neTouchePasAuxFichiersQuiNeSontPasLesNotres(@TempDir Path tmp) throws Exception {
        Path dossier = dossier(tmp);
        Files.createDirectories(dossier);
        Path lisezMoi = dossier.resolve("LISEZ-MOI.md");
        Files.writeString(lisezMoi, "ces diagrammes sont engendrés", StandardCharsets.UTF_8);

        ecrire(tmp);

        assertTrue(Files.exists(lisezMoi),
            "Le dossier de sortie n'est pas vidé de ce qui ne nous appartient pas.");
    }

    @Test
    void keep_conserveLaGenerationPrecedente(@TempDir Path tmp) throws Exception {
        ecrire(projet(), tmp, ".svg", true);
        ecrire(projet(), tmp, ".svg", true);

        assertTrue(Files.exists(dossier(tmp).resolve("projet.svg")), "La première reste.");
        assertTrue(Files.exists(tmp.resolve("diagrammes-2/projet.svg")),
            "La seconde est écrite à côté, pour pouvoir les comparer.");
    }

    @Test
    void keep_numeroteAutantDeFoisQueNecessaire(@TempDir Path tmp) throws Exception {
        for (int i = 0; i < 3; i++) {
            ecrire(projet(), tmp, ".svg", true);
        }
        assertTrue(Files.exists(tmp.resolve("diagrammes-3/projet.svg")), fichiers(tmp).toString());
    }
}
