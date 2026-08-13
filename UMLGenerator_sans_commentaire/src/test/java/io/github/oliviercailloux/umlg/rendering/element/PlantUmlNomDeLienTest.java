package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PlantUmlNomDeLienTest {

    private static final Pattern NODESEP = Pattern.compile("skinparam nodesep (\\d+)");

    private static UmlEntity[] paire(UmlAssociation association) {
        return new UmlEntity[] {
            UmlEntity.builder("Source", "Source")
                .associations(ImmutableList.of(association)).build(),
            UmlEntity.builder(association.targetQualifiedName(), "Cible").build()};
    }

    private static int nodesep(String source) {
        Matcher m = NODESEP.matcher(source);
        assertTrue(m.find(), () -> "Aucune séparation déclarée :\n" + source);
        return Integer.parseInt(m.group(1));
    }

    private static String ligneDeTrait(String source) {
        return source.lines()
            .filter(l -> l.startsWith("Source "))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Aucun trait :\n" + source));
    }


    @Test
    void nomDeLienSurDeuxLignes_habilleChaqueLigneSeparement() {
        String source = PlantUmlAssert.documentPour(UmlStyle.UMLG,
            paire(UmlAssociation.builder("champ", "Cible").label("lig1\nlig2").build()));

        String trait = ligneDeTrait(source);

        assertTrue(trait.contains("<i>lig1</i>"), trait);
        assertTrue(trait.contains("<i>lig2</i>"), trait);
        assertFalse(trait.contains("lig1\\nlig2"),
            "Les deux lignes ne doivent plus partager un seul habillage : " + trait);
        assertTrue(trait.contains("</i></color>\\n"),
            "Le passage à la ligne reste, entre deux habillages complets : " + trait);
    }

    @Test
    void nomDeLienSurDeuxLignes_neDessinePasSesBalises() throws Exception {
        String svg = PlantUmlAssert.svg(UmlStyle.UMLG,
            paire(UmlAssociation.builder("champ", "Cible").label("lig1\nlig2").build()));

        assertFalse(svg.contains("&lt;/i&gt;"), "Une balise fermante est dessinée :\n" + svg);
        assertFalse(svg.contains("&lt;/color&gt;"), "Une balise fermante est dessinée :\n" + svg);
    }

    @Test
    void nomDeLienSurUneLigne_estHabilleCommeAvant() {
        String trait = ligneDeTrait(PlantUmlAssert.documentPour(UmlStyle.UMLG,
            paire(UmlAssociation.builder("champ", "Cible").label("détient").build())));

        assertTrue(trait.endsWith(" : <color:#3C5F82><i>détient</i></color>"), trait);
    }


    @Test
    void nomDeLienFaitDeCaracteresInvisibles_nEcritAucunHabillage() {
        String trait = ligneDeTrait(PlantUmlAssert.documentPour(UmlStyle.UMLG,
            paire(UmlAssociation.builder("champ", "Cible").label("\1\2").build())));

        assertFalse(trait.contains("<i>"), "Un habillage vide reste dessiné : " + trait);
        assertFalse(trait.contains(" : "), "Le trait ne doit porter aucun nom : " + trait);
    }

    @Test
    void ligneVideAuMilieuDunNom_neRecoitPasDhabillage() {
        String trait = ligneDeTrait(PlantUmlAssert.documentPour(UmlStyle.PLANTUML,
            paire(UmlAssociation.builder("champ", "Cible").label("haut\n\nbas").build())));

        assertTrue(trait.endsWith(" : <i>haut</i>\\n\\n<i>bas</i>"), trait);
    }


    @Test
    void nomDeLienLong_ecarteLesBoites() {
        String court = PlantUmlAssert.documentPour(
            paire(UmlAssociation.builder("champ", "Cible").label("lit").build()));
        String long_ = PlantUmlAssert.documentPour(paire(UmlAssociation.builder("champ", "Cible")
            .label("consulte le référentiel des tarifs").build()));

        assertTrue(nodesep(long_) > nodesep(court),
            () -> "Court : " + nodesep(court) + ", long : " + nodesep(long_));
    }

    @Test
    void largeurMesuree_ignoreLaFormeEchappee() {
        String guillemets = PlantUmlAssert.documentPour(
            paire(UmlAssociation.builder("champ", "Cible").targetRole("\"\"\"\"\"\"\"\"").build()));
        String lettres = PlantUmlAssert.documentPour(
            paire(UmlAssociation.builder("champ", "Cible").targetRole("abcdefgh").build()));

        assertEquals(nodesep(lettres), nodesep(guillemets),
            "Huit guillemets et huit lettres occupent la même place sur le dessin.");
    }

    @Test
    void nomDeLienSurDeuxLignes_estMesureSurSaPlusLongueLigne() {
        String surDeuxLignes = PlantUmlAssert.documentPour(
            paire(UmlAssociation.builder("champ", "Cible").label("abcdefgh\nabcdefgh").build()));
        String surUneLigne = PlantUmlAssert.documentPour(
            paire(UmlAssociation.builder("champ", "Cible").label("abcdefgh").build()));

        assertEquals(nodesep(surUneLigne), nodesep(surDeuxLignes));
    }

    @Test
    void extremitesEtNomDeLien_sontMesuresEnsemble() {
        List<UmlEntity[]> paires = List.of(
            paire(UmlAssociation.builder("champ", "Cible").targetRole("abcdefghij").build()),
            paire(UmlAssociation.builder("champ", "Cible").label("abcdefghij").build()));

        assertEquals(nodesep(PlantUmlAssert.documentPour(paires.get(0))),
            nodesep(PlantUmlAssert.documentPour(paires.get(1))),
            "Un rôle de dix caractères et un nom de lien de dix caractères réservent autant.");
    }
}
