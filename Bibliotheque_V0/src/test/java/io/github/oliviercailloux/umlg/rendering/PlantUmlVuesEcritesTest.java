package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

// Ce que les vues laissent sur le disque : l'arborescence, et le sort de la génération
// précédente.
class PlantUmlVuesEcritesTest {

    private static UmlEntity boite(String nom) {
        return UmlEntity.builder("com.exemple." + nom, nom).build();
    }

    // Un projet à trois vues : l'intégrale, puis deux vues déclarées.
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

    @Test
    void lIntegraleEstALaRacine_lesAutresDansUnSousDossier(@TempDir Path tmp) throws Exception {
        Path dossier = tmp.resolve("diagrammes");

        ImmutableList<Path> ecrits = PlantUmlWriter.writeViews(projet(), dossier, ".svg",
            UmlStyle.PLANTUML, Regles.Sortie.REPLACE);

        assertEquals(3, ecrits.size(), ecrits.toString());
        assertEquals(List.of("projet.svg", "vues"), fichiers(dossier));
        assertEquals(List.of("clients.svg", "facturation.svg"),
            fichiers(dossier.resolve("vues")));
    }

    @Test
    void chaqueFichier_contientSaSeuleVue(@TempDir Path tmp) throws Exception {
        Path dossier = tmp.resolve("diagrammes");
        PlantUmlWriter.writeViews(projet(), dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.REPLACE);

        String facturation =
            Files.readString(dossier.resolve("vues/facturation.svg"), StandardCharsets.UTF_8);

        assertTrue(facturation.contains("Facture"), "La classe retenue doit être dessinée.");
        assertFalse(facturation.contains("Client"), "Celle que la vue écarte ne doit pas l'être.");
    }

    @Test
    void chaqueVue_porteSonTitre(@TempDir Path tmp) throws Exception {
        Path dossier = tmp.resolve("diagrammes");
        PlantUmlWriter.writeViews(projet(), dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.REPLACE);

        assertTrue(Files.readString(dossier.resolve("vues/clients.svg"), StandardCharsets.UTF_8)
            .contains("Clients"), "Le nom de la vue lui sert de titre.");
    }

    @Test
    void aucuneImageDErreur(@TempDir Path tmp) throws Exception {
        Path dossier = tmp.resolve("diagrammes");
        PlantUmlWriter.writeViews(projet(), dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.REPLACE);

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
        // Son titre est déclaré et peut changer ; son chemin, lui, ne doit pas — c'est le
        // diagramme qu'on ouvre en premier, et celui vers lequel une documentation pointe.
        ImmutableList<UmlEntity> toutes = ImmutableList.of(boite("Facture"));
        UmlProject projet = UmlProject.given(Set.copyOf(toutes), UmlDiagram.defaut(),
            List.of(new UmlView("Architecture du module Facturation", toutes,
                UMLFlow.TOP_TO_BOTTOM)));
        Path dossier = tmp.resolve("diagrammes");

        PlantUmlWriter.writeViews(projet, dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.REPLACE);

        assertTrue(Files.exists(dossier.resolve("projet.svg")), fichiers(dossier).toString());
        assertTrue(Files.readString(dossier.resolve("projet.svg"), StandardCharsets.UTF_8)
            .contains("Architecture du module Facturation"), "Le titre, lui, est bien écrit.");
    }

    @Test
    void formatPng_estAccepte(@TempDir Path tmp) throws Exception {
        Path dossier = tmp.resolve("diagrammes");

        PlantUmlWriter.writeViews(projet(), dossier, ".png", UmlStyle.PLANTUML,
            Regles.Sortie.REPLACE);

        assertEquals(List.of("projet.png", "vues"), fichiers(dossier));
    }

    // --- La navigation d'une vue à l'autre ---

    @Test
    void depuisLIntegrale_chaqueBoiteMeneAsaVue(@TempDir Path tmp) throws Exception {
        // Un dossier de diagrammes n'est qu'une collection d'images tant qu'on ne peut pas
        // passer de l'une à l'autre. Depuis la vue d'ensemble, cliquer sur une classe doit
        // ouvrir celle qui la détaille.
        Path dossier = tmp.resolve("diagrammes");
        PlantUmlWriter.writeViews(projet(), dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.REPLACE);

        String integrale =
            Files.readString(dossier.resolve("projet.svg"), StandardCharsets.UTF_8);

        assertTrue(integrale.contains("vues/facturation.svg"), "Le lien descend au sous-dossier.");
        assertTrue(integrale.contains("vues/clients.svg"), integrale.substring(0, 400));
    }

    @Test
    void entreDeuxVues_leCheminEstCeluiDuVoisin(@TempDir Path tmp) throws Exception {
        // Depuis « vues/facturation.svg », les autres vues sont des voisines : le chemin ne
        // repasse pas par la racine.
        Path dossier = tmp.resolve("diagrammes");
        PlantUmlWriter.writeViews(projet(), dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.REPLACE);

        String clients =
            Files.readString(dossier.resolve("vues/clients.svg"), StandardCharsets.UTF_8);

        assertFalse(clients.contains("vues/vues/"), "Aucun chemin ne se dédouble.");
    }

    @Test
    void uneVue_neRenvoiePasVersElleMeme(@TempDir Path tmp) throws Exception {
        // Le lien ne mènerait nulle part, et laisserait croire qu'il y a autre chose à voir.
        Path dossier = tmp.resolve("diagrammes");
        PlantUmlWriter.writeViews(projet(), dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.REPLACE);

        String facturation =
            Files.readString(dossier.resolve("vues/facturation.svg"), StandardCharsets.UTF_8);

        assertFalse(facturation.contains("facturation.svg"), facturation.substring(0, 400));
    }

    @Test
    void uneBoiteDeclareeEtCliquable_estAccepteeParLeMoteur(@TempDir Path tmp) throws Exception {
        // Le piège que ce test protège : le moteur accepte « <<stéréotype>> [[lien]] #style » et
        // refuse « <<stéréotype>> #style [[lien]] ». Rien ne le documente, et seul le cas où une
        // boîte déclarée porte AUSSI un lien de navigation le révèle — les tests qui éprouvaient
        // l'un sans l'autre passaient tous.
        UmlEntity declaree = UmlEntity.builder("Animal", "Animal").declared(true).build();
        ImmutableList<UmlEntity> toutes = ImmutableList.of(boite("Facture"), declaree);

        UmlProject projet = UmlProject.given(Set.copyOf(toutes), UmlDiagram.defaut(), List.of(
            new UmlView("Projet", toutes, UMLFlow.TOP_TO_BOTTOM),
            new UmlView("Le vivant", ImmutableList.of(declaree), UMLFlow.TOP_TO_BOTTOM)));
        Path dossier = tmp.resolve("diagrammes");

        // writeViews échoue si le moteur rejette la source : la vérification est dans le writer,
        // et c'est elle qui a attrapé le défaut au lieu de livrer une image d'erreur.
        PlantUmlWriter.writeViews(projet, dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.REPLACE);

        String integrale =
            Files.readString(dossier.resolve("projet.svg"), StandardCharsets.UTF_8);

        assertTrue(integrale.contains("vues/le-vivant.svg"), "Le lien est bien posé.");
        assertTrue(integrale.contains("stroke-dasharray"), "Et la boîte reste en pointillés.");
        assertFalse(integrale.toLowerCase().contains("syntax error"), "Aucune image d'erreur.");
    }

    // --- Ce que devient la génération précédente ---

    @Test
    void replace_effaceLesDiagrammesDevenusInutiles(@TempDir Path tmp) throws Exception {
        Path dossier = tmp.resolve("diagrammes");
        Files.createDirectories(dossier.resolve("vues"));
        // Une vue d'une génération passée, que le fichier de règles ne déclare plus.
        Path fantome = dossier.resolve("vues/ancienne-vue.svg");
        Files.writeString(fantome, "<svg/>", StandardCharsets.UTF_8);

        PlantUmlWriter.writeViews(projet(), dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.REPLACE);

        assertFalse(Files.exists(fantome),
            "Une image que plus rien ne met à jour ment d'autant mieux qu'elle est à sa place.");
    }

    @Test
    void replace_neTouchePasAuxFichiersQuiNeSontPasLesNotres(@TempDir Path tmp) throws Exception {
        Path dossier = tmp.resolve("diagrammes");
        Files.createDirectories(dossier);
        Path lisezMoi = dossier.resolve("LISEZ-MOI.md");
        Files.writeString(lisezMoi, "ces diagrammes sont engendrés", StandardCharsets.UTF_8);

        PlantUmlWriter.writeViews(projet(), dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.REPLACE);

        assertTrue(Files.exists(lisezMoi),
            "Le dossier de sortie n'est pas vidé de ce qui ne nous appartient pas.");
    }

    @Test
    void keep_conserveLaGenerationPrecedente(@TempDir Path tmp) throws Exception {
        Path dossier = tmp.resolve("diagrammes");

        PlantUmlWriter.writeViews(projet(), dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.KEEP);
        PlantUmlWriter.writeViews(projet(), dossier, ".svg", UmlStyle.PLANTUML,
            Regles.Sortie.KEEP);

        assertTrue(Files.exists(dossier.resolve("projet.svg")), "La première reste.");
        assertTrue(Files.exists(tmp.resolve("diagrammes-2/projet.svg")),
            "La seconde est écrite à côté, pour pouvoir les comparer.");
    }

    @Test
    void keep_numeroteAutantDeFoisQueNecessaire(@TempDir Path tmp) throws Exception {
        Path dossier = tmp.resolve("diagrammes");
        for (int i = 0; i < 3; i++) {
            PlantUmlWriter.writeViews(projet(), dossier, ".svg", UmlStyle.PLANTUML,
                Regles.Sortie.KEEP);
        }
        assertTrue(Files.exists(tmp.resolve("diagrammes-3/projet.svg")), fichiers(tmp).toString());
    }
}
