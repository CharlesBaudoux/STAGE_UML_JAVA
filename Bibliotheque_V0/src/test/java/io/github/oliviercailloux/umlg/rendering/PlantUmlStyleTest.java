package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// Le choix d'apparence : ce qu'il change, et surtout ce qu'il ne change pas.
//
// Une apparence ne doit toucher qu'à l'habillage. Le contrat vérifié ici est donc double :
// chaque style produit bien le rendu qu'il annonce, et les deux disent exactement la même chose
// — mêmes boîtes, mêmes membres, mêmes traits. Sans cette seconde moitié, « changer le thème »
// pourrait discrètement changer le diagramme.
class PlantUmlStyleTest {

    // Les couleurs que PlantUML applique de lui-même, et que le thème de la bibliothèque
    // remplace. Lues dans le SVG rendu : c'est le seul endroit où une couleur est observable.
    private static final String GRIS_PLANTUML = "#F1F1F1";
    private static final String PASTILLE_CLASSE = "#ADD1B2";
    private static final String BANDEAU_UMLG = "#DDE7F0";

    private static UmlEntity boite() {
        return UmlEntity.builder("com.exemple.Client", "Client")
            .fields(List.of(new UmlField("solde", "double", "-", false))).build();
    }

    // Deux boîtes reliées par un lien nommé : de quoi observer aussi l'habillage du nom.
    private static UmlEntity[] paire() {
        return new UmlEntity[] {
            UmlEntity.builder("com.exemple.Client", "Client")
                .associations(ImmutableList.of(UmlAssociation.builder("champ", "com.exemple.Cible")
                    .targetCardinality("0..*").label("détient").build()))
                .build(),
            UmlEntity.builder("com.exemple.Cible", "Cible").build()};
    }

    // --- Ce que chaque apparence écrit dans la source ---

    @Test
    void parDefaut_aucuneCouleurNestImposee() {
        // L'apparence par défaut ne doit rien décider du tout : ni couleur de boîte, ni couleur
        // de trait. C'est ce qui la rend reconnaissable — un diagramme PlantUML ordinaire.
        String source = PlantUmlAssert.documentPour(boite());

        assertFalse(source.contains("ClassBackgroundColor"), source);
        assertFalse(source.contains("ClassHeaderBackgroundColor"), source);
        assertFalse(source.contains("ClassBorderColor"), source);
        assertFalse(source.contains("ArrowColor"), source);
    }

    @Test
    void parDefaut_lapastilleNestPasMasquee() {
        // « hide circle » supprimerait le C/I/E accolé au nom. Le laisser est précisément ce qui
        // fait ressembler le défaut à un diagramme dessiné à la main.
        assertFalse(PlantUmlAssert.documentPour(boite()).contains("hide circle"));
    }

    @Test
    void themeUmlg_imposeSesCouleursEtMasqueLaPastille() {
        String source = PlantUmlAssert.documentPour(UmlStyle.UMLG, boite());

        assertTrue(source.contains("ClassHeaderBackgroundColor " + BANDEAU_UMLG), source);
        assertTrue(source.contains("hide circle"), source);
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void touteApparence_conserveCeQuiTientAuSens(UmlStyle style) {
        // Trois réglages échappent au choix. Les visibilités s'écrivent « + » et « - », la
        // notation d'UML, et non en pastilles colorées ; une boîte sans membre ne montre pas de
        // compartiment vide ; l'espacement reste calculé sur les étiquettes.
        String source = PlantUmlAssert.documentPour(style, boite());

        assertTrue(source.contains("skinparam classAttributeIconSize 0"), source);
        assertTrue(source.contains("hide empty members"), source);
        assertTrue(source.contains("skinparam nodesep "), source);
        assertTrue(source.contains("skinparam ranksep "), source);
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void touteApparence_estAccepteeParPlantUml(UmlStyle style) {
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(style, paire()));
    }

    // --- Ce que le lecteur voit ---

    @Test
    void parDefaut_leRenduEstCeluiDePlantUml() throws Exception {
        String svg = PlantUmlAssert.svg(boite());

        assertTrue(svg.contains(GRIS_PLANTUML),
            () -> "Le fond gris de PlantUML est attendu :\n" + svg);
        assertTrue(svg.contains(PASTILLE_CLASSE),
            () -> "La pastille « C » verte est attendue :\n" + svg);
    }

    @Test
    void themeUmlg_remplaceLeRenduDePlantUml() throws Exception {
        String svg = PlantUmlAssert.svg(UmlStyle.UMLG, boite());

        assertTrue(svg.contains(BANDEAU_UMLG), () -> "Le bandeau bleu est attendu :\n" + svg);
        assertFalse(svg.contains(PASTILLE_CLASSE),
            () -> "La pastille doit être masquée sous ce thème :\n" + svg);
    }

    // --- Le nom d'un lien ---

    @Test
    void nomDeLien_estColoreParLeThemeSeulement() {
        // La couleur relève du goût, elle suit donc le thème.
        assertFalse(PlantUmlAssert.documentPour(paire()).contains("<color:"),
            "L'apparence par défaut ne colore rien.");
        assertTrue(PlantUmlAssert.documentPour(UmlStyle.UMLG, paire()).contains("<color:"),
            "Le thème de la bibliothèque colore le nom des liens.");
    }

    @ParameterizedTest
    @EnumSource(UmlStyle.class)
    void nomDeLien_resteEnItaliqueDansLesDeuxCas(UmlStyle style) {
        // L'italique n'est pas une coquetterie : la cardinalité se pose au même endroit que le
        // nom du lien, et rien d'autre ne permet au lecteur de savoir lequel nomme le trait.
        // Il est donc commun aux deux apparences.
        assertTrue(PlantUmlAssert.documentPour(style, paire()).contains("<i>détient</i>"));
    }

    // --- L'invariant : l'apparence ne change pas ce qui est dit ---

    @Test
    void lesDeuxApparences_disentExactementLaMemeChose() {
        // Comparaison de tout ce qui n'est pas du style : les déclarations de boîte et les
        // lignes de lien, habillage retiré. Elles doivent coïncider mot pour mot.
        assertEquals(PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(paire())),
            PlantUmlAssert.nomsDeclares(PlantUmlAssert.documentPour(UmlStyle.UMLG, paire())));

        assertEquals(PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(paire())),
            PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(UmlStyle.UMLG, paire())));
    }

    @Test
    void lesDeuxApparences_neProduisentPasLeMemeDocument() {
        // Le pendant du test précédent : elles disent la même chose, mais elles ne l'écrivent
        // pas pareil. Sans cette assertion, un style qui ne ferait rien passerait inaperçu.
        assertNotEquals(PlantUmlAssert.documentPour(paire()),
            PlantUmlAssert.documentPour(UmlStyle.UMLG, paire()));
    }
}
