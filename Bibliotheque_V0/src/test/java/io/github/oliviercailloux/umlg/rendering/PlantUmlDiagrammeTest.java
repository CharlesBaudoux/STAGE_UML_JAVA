package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// Ce que @UMLDiagram écrit en tête du fichier engendré : le titre, le sens de lecture, le sort
// des compartiments vides.
//
// Les trois se rendent au même endroit et pour la même raison — ils ne visent aucune boîte —,
// d'où un seul fichier de test. Ce qu'il faut prouver de chacun est double : la directive
// attendue est écrite quand on la demande, et rien n'est écrit quand on ne demande rien. Une
// directive posée « au cas où » n'est pas neutre : elle se lit dans la source, et ment sur ce
// que l'auteur a déclaré.
class PlantUmlDiagrammeTest {

    private static final String LIGNE_MASQUAGE = "hide empty members";
    private static final String LIGNE_GAUCHE_DROITE = "left to right direction";

    // Une boîte vide : le cas que hideEmptyMembers concerne, celui de strategy = NONE.
    private static UmlEntity boiteVide() {
        return UmlEntity.builder("com.exemple.Client", "Client").build();
    }

    private static UmlEntity boitePeuplee() {
        return UmlEntity.builder("com.exemple.Compte", "Compte")
            .fields(List.of(new UmlField("solde", "double", "-", false))).build();
    }

    private static UmlDiagram reglages(String titre, UMLFlow sens, boolean masquer) {
        return new UmlDiagram(titre, sens, masquer);
    }

    // --- Le titre ---

    @Test
    void titreDeclare_estEcritEnTete() {
        String source = PlantUmlAssert.documentPour(
            reglages("Architecture du module Facturation", UMLFlow.TOP_TO_BOTTOM, true),
            boiteVide());

        assertTrue(source.contains("title Architecture du module Facturation"), source);
    }

    @Test
    void sansTitre_aucuneLigneTitreNestEcrite() {
        // Pas même « title » suivi de rien : la ligne occuperait de la hauteur en tête du dessin.
        assertFalse(PlantUmlAssert.documentPour(boiteVide()).contains("title"),
            PlantUmlAssert.documentPour(boiteVide()));
    }

    @Test
    void titre_estEchappe() {
        // PlantUML lit « title » jusqu'à la fin de la ligne : un chevron y serait pris pour du
        // balisage, et le texte de l'utilisateur ne doit jamais en devenir.
        String source = PlantUmlAssert.documentPour(
            reglages("Vue <b>générale</b>", UMLFlow.TOP_TO_BOTTOM, true), boiteVide());

        assertFalse(source.contains("<b>"), source);
        assertTrue(source.contains("&#60;b&#62;"), source);
    }

    @Test
    void titre_precedeLesBoites() {
        // Le titre n'a pas à être en tête pour que PlantUML le comprenne ; il y est pour que la
        // source engendrée se lise. Le test fige donc cet ordre, qu'aucune passe ajoutée au
        // writer ne doit défaire.
        String source =
            PlantUmlAssert.documentPour(reglages("Facturation", UMLFlow.TOP_TO_BOTTOM, true),
                boiteVide());

        assertTrue(source.indexOf("title Facturation") < source.indexOf("class \"Client\""),
            source);
    }

    // --- Le sens de lecture ---

    @Test
    void sensGaucheADroite_estDemandeAPlantUml() {
        String source = PlantUmlAssert.documentPour(
            reglages("", UMLFlow.LEFT_TO_RIGHT, true), boiteVide());

        assertTrue(source.contains(LIGNE_GAUCHE_DROITE), source);
    }

    @Test
    void sensParDefaut_nEcritAucuneDirective() {
        // TOP_TO_BOTTOM est déjà ce que PlantUML fait sans qu'on le lui demande : l'écrire
        // laisserait croire à une décision là où il n'y en a pas.
        String source = PlantUmlAssert.documentPour(boiteVide());

        assertFalse(source.contains(LIGNE_GAUCHE_DROITE), source);
        assertFalse(source.contains("top to bottom direction"), source);
    }

    // --- Les compartiments vides ---

    @Test
    void parDefaut_lesCompartimentsVidesSontMasques() {
        assertTrue(PlantUmlAssert.documentPour(boiteVide()).contains(LIGNE_MASQUAGE));
    }

    @Test
    void masquageRefuse_laDirectiveDisparait() {
        String source = PlantUmlAssert.documentPour(
            reglages("", UMLFlow.TOP_TO_BOTTOM, false), boiteVide());

        assertFalse(source.contains(LIGNE_MASQUAGE), source);
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void leMasquage_neDependPasDeLApparence(UmlStyle style) {
        // Le point de la refonte : ce réglage a quitté le bloc commun aux deux apparences, mais
        // il ne leur appartenait pas pour autant. Les deux styles doivent le suivre à
        // l'identique — c'est @UMLDiagram qui décide, jamais UmlStyle.
        assertTrue(PlantUmlAssert.documentPour(style, UmlDiagram.defaut(), boiteVide())
            .contains(LIGNE_MASQUAGE));
        assertFalse(PlantUmlAssert
            .documentPour(style, reglages("", UMLFlow.TOP_TO_BOTTOM, false), boiteVide())
            .contains(LIGNE_MASQUAGE));
    }

    // --- Ce que les réglages ne changent pas ---

    @Test
    void lesReglagesDEnsemble_neTouchentAAucuneBoite() {
        // Un titre et un sens de lecture disent comment le dessin se présente, jamais ce qu'il
        // contient. Les boîtes engendrées doivent donc être exactement les mêmes.
        UmlEntity[] entites = {boiteVide(), boitePeuplee()};

        assertEquals(
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(entites)),
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(
                reglages("Un titre", UMLFlow.LEFT_TO_RIGHT, false), entites)));
    }

    @Test
    void toutesLesDirectives_sontAccepteesParPlantUml() {
        // Le filet de sécurité : la source engendrée est-elle lue sans reproche par le moteur ?
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(
            reglages("Architecture « Facturation »", UMLFlow.LEFT_TO_RIGHT, false),
            boiteVide(), boitePeuplee()));
    }
}
