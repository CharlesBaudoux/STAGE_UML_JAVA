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

// Le texte posé sur un trait : son habillage, et la place qu'on lui réserve.
//
// Deux sujets qui n'en font qu'un, parce qu'ils portent sur le même texte. L'habillage doit
// survivre à un passage à la ligne — PlantUML coupe l'étiquette sans reporter les balises
// ouvertes —, et l'espacement du diagramme doit être calculé sur ce que le lecteur voit, pas sur
// la forme échappée qui s'écrit dans le fichier.
class PlantUmlNomDeLienTest {

    private static final Pattern NODESEP = Pattern.compile("skinparam nodesep (\\d+)");

    // Le duo minimal : une relation a besoin de ses deux extrémités déclarées.
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

    // La ligne de trait, telle qu'elle est écrite dans la source — habillage compris, que
    // PlantUmlAssert.lignesDeLien retire justement.
    private static String ligneDeTrait(String source) {
        return source.lines()
            .filter(l -> l.startsWith("Source "))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Aucun trait :\n" + source));
    }

    // --- Un nom de lien sur deux lignes ---

    @Test
    void nomDeLienSurDeuxLignes_habilleChaqueLigneSeparement() {
        // Le défaut : « <i>lig1\nlig2</i> ». PlantUML coupe au « \n » sans reporter les balises,
        // et la seconde ligne perdait son italique tout en affichant les balises fermantes.
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
        // Le niveau qui compte : ce que le lecteur voit. « lig2</i></color> » s'affichait en
        // toutes lettres sur le trait, et rien ne l'attrapait — la source est valide, donc la
        // garde du writer ne voyait pas d'erreur.
        String svg = PlantUmlAssert.svg(UmlStyle.UMLG,
            paire(UmlAssociation.builder("champ", "Cible").label("lig1\nlig2").build()));

        assertFalse(svg.contains("&lt;/i&gt;"), "Une balise fermante est dessinée :\n" + svg);
        assertFalse(svg.contains("&lt;/color&gt;"), "Une balise fermante est dessinée :\n" + svg);
    }

    @Test
    void nomDeLienSurUneLigne_estHabilleCommeAvant() {
        // Le cas nominal ne doit rien gagner : un seul habillage, et pas de « \n ».
        String trait = ligneDeTrait(PlantUmlAssert.documentPour(UmlStyle.UMLG,
            paire(UmlAssociation.builder("champ", "Cible").label("détient").build())));

        assertTrue(trait.endsWith(" : <color:#3C5F82><i>détient</i></color>"), trait);
    }

    // --- Un nom que l'échappement vide ---

    @Test
    void nomDeLienFaitDeCaracteresInvisibles_nEcritAucunHabillage() {
        // L'échappement supprime les caractères de contrôle et peut donc vider la chaîne. Le
        // garde en amont testait le texte d'origine, non vide : « <i></i> » était écrit, et
        // PlantUML le dessine « </i> » sur le trait.
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

    // --- L'espacement, mesuré sur ce qui se dessine ---

    @Test
    void nomDeLienLong_ecarteLesBoites() {
        // UmlStyle annonce « l'espacement, calculé sur la plus longue étiquette du diagramme ».
        // Seules les extrémités étaient mesurées ; le nom du lien — le texte le plus long d'un
        // trait en pratique — se dessine pourtant au même endroit, entre deux boîtes voisines.
        String court = PlantUmlAssert.documentPour(
            paire(UmlAssociation.builder("champ", "Cible").label("lit").build()));
        String long_ = PlantUmlAssert.documentPour(paire(UmlAssociation.builder("champ", "Cible")
            .label("consulte le référentiel des tarifs").build()));

        assertTrue(nodesep(long_) > nodesep(court),
            () -> "Court : " + nodesep(court) + ", long : " + nodesep(long_));
    }

    @Test
    void largeurMesuree_ignoreLaFormeEchappee() {
        // Un guillemet occupe un caractère à l'écran et cinq dans la source (« &#34; »). Mesurée
        // sur la source, une étiquette de guillemets passait pour cinq fois plus large qu'elle
        // n'est, et les boîtes s'écartaient pour de la place que rien n'occupe.
        String guillemets = PlantUmlAssert.documentPour(
            paire(UmlAssociation.builder("champ", "Cible").targetRole("\"\"\"\"\"\"\"\"").build()));
        String lettres = PlantUmlAssert.documentPour(
            paire(UmlAssociation.builder("champ", "Cible").targetRole("abcdefgh").build()));

        assertEquals(nodesep(lettres), nodesep(guillemets),
            "Huit guillemets et huit lettres occupent la même place sur le dessin.");
    }

    @Test
    void nomDeLienSurDeuxLignes_estMesureSurSaPlusLongueLigne() {
        // Deux lignes se dessinent l'une sous l'autre : la largeur est celle de la plus longue,
        // et non leur somme.
        String surDeuxLignes = PlantUmlAssert.documentPour(
            paire(UmlAssociation.builder("champ", "Cible").label("abcdefgh\nabcdefgh").build()));
        String surUneLigne = PlantUmlAssert.documentPour(
            paire(UmlAssociation.builder("champ", "Cible").label("abcdefgh").build()));

        assertEquals(nodesep(surUneLigne), nodesep(surDeuxLignes));
    }

    @Test
    void extremitesEtNomDeLien_sontMesuresEnsemble() {
        // Les deux textes se disputent la même place ; c'est le plus large qui décide.
        List<UmlEntity[]> paires = List.of(
            paire(UmlAssociation.builder("champ", "Cible").targetRole("abcdefghij").build()),
            paire(UmlAssociation.builder("champ", "Cible").label("abcdefghij").build()));

        assertEquals(nodesep(PlantUmlAssert.documentPour(paires.get(0))),
            nodesep(PlantUmlAssert.documentPour(paires.get(1))),
            "Un rôle de dix caractères et un nom de lien de dix caractères réservent autant.");
    }
}
