package io.github.oliviercailloux.umlg.rendering.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

// La lisibilité du diagramme produit — ce que les autres tests ne regardent pas.
//
// Deux sujets : distinguer visuellement le nom d'un lien de ses multiplicités (elles se posent
// au même endroit, et PlantUML seul décide où), et réserver assez de place entre les boîtes
// pour que les étiquettes ne se chevauchent pas.
//
// Ces tests lisent le SVG rendu, seul endroit où la couleur et l'italique sont observables.
class PlantUmlLisibiliteTest {

    // Capture un texte du SVG avec son apparence : groupe 1 la couleur, groupe 2 les autres
    // attributs (où se trouve l'italique), groupe 3 le contenu.
    private static final Pattern TEXTE_SVG =
        Pattern.compile("<text[^>]*fill=\"(#[0-9A-Fa-f]{6})\"([^>]*)>([^<]*)</text>");

    private static final Pattern NODESEP = Pattern.compile("skinparam nodesep (\\d+)");

    // Fabrique le duo minimal source + cible : une relation a besoin de ses deux extrémités
    // déclarées, sinon AliasRegistry refuse la cible.
    private static UmlEntity[] paire(UmlAssociation association) {
        return new UmlEntity[] {
            UmlEntity.builder("Source", "Source")
                .associations(ImmutableList.of(association)).build(),
            UmlEntity.builder(association.targetQualifiedName(), "Cible").build()};
    }

    // L'espacement horizontal effectivement demandé à PlantUML, extrait du bloc de style.
    private static int nodesep(String source) {
        Matcher m = NODESEP.matcher(source);
        assertTrue(m.find(), () -> "Aucune séparation déclarée :\n" + source);
        return Integer.parseInt(m.group(1));
    }

    private static String decode(String fragment) {
        Matcher m = Pattern.compile("&#(\\d+);").matcher(fragment);
        StringBuilder sortie = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sortie,
                Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
        }
        return m.appendTail(sortie).toString();
    }

    // Rend l'apparence d'un texte donné, sous la forme « #RRGGBB italic » ou « #RRGGBB droit ».
    // C'est cette chaîne que les tests comparent : peu importe la couleur exacte, seul compte le
    // fait que deux textes se ressemblent ou non.
    private static String apparence(String svg, String contenu) {
        Matcher m = TEXTE_SVG.matcher(svg);
        while (m.find()) {
            if (decode(m.group(3)).strip().equals(contenu)) {
                return m.group(1) + (m.group(2).contains("italic") ? " italic" : " droit");
            }
        }
        throw new AssertionError("Texte « " + contenu + " » absent du rendu :\n" + svg);
    }

    // --- Distinguer le nom du lien de ses multiplicités ---

    @Test
    void nomDuLien_etMultiplicite_neSeRessemblentPas() throws Exception {
        String svg = PlantUmlAssert.svg(paire(UmlAssociation.builder("champ", "Cible")
            .targetMultiplicity("0..*").label("détient").build()));

        // La multiplicité est régulièrement posée sur le trait, voire traversée par lui : c'est
        // PlantUML qui en décide, et aucun réglage n'y change rien. Ne reste que l'apparence
        // pour que le lecteur sache lequel des deux textes nomme le lien.
        assertNotEquals(apparence(svg, "0..*"), apparence(svg, "détient"),
            "Le nom du lien et la multiplicité doivent se distinguer au premier coup d'œil.");
    }

    @Test
    void nomDeDependance_estHabilleCommeUnNomDassociation() throws Exception {
        UmlEntity source = UmlEntity.builder("Source", "Source")
            .dependencies(ImmutableList.of(new UmlDependency("Cible", "audite"))).build();
        String svg = PlantUmlAssert.svg(source, UmlEntity.builder("Cible", "Cible").build());

        String assoc = PlantUmlAssert.svg(paire(UmlAssociation.builder("champ", "Cible")
            .label("audite").build()));

        assertEquals(apparence(assoc, "audite"), apparence(svg, "audite"),
            "Association et dépendance nomment toutes deux un trait : même traitement.");
    }

    @Test
    void nomDuLien_survitAUnDoubleSlashDansLeTexte() throws Exception {
        // « // » ouvre l'italique en Creole. Si l'habillage s'appuyait sur ce marqueur apparié,
        // un tel texte le refermerait au milieu et déteindrait sur la suite du diagramme.
        String svg = PlantUmlAssert.svg(paire(UmlAssociation.builder("champ", "Cible")
            .label("lit//écrit").build()));

        assertTrue(decode(svg).contains("lit//écrit"),
            () -> "Le texte doit s'afficher tel quel, « // » compris :\n" + svg);
    }

    // --- Réserver la place des étiquettes ---

    @Test
    void etiquetteLongue_ecarteDavantageLesBoites() {
        String court = PlantUmlAssert.documentPour(paire(
            UmlAssociation.builder("champ", "Cible").targetMultiplicity("1").build()));
        String long_ = PlantUmlAssert.documentPour(paire(
            UmlAssociation.builder("champ", "Cible")
                .targetRole("comptesDuTitulaire").targetMultiplicity("1..*")
                .constraints(ImmutableList.of("ordered")).build()));

        assertTrue(nodesep(long_) > nodesep(court),
            () -> "Une étiquette d'extrémité se dessine entre deux boîtes : plus elle est "
                + "longue, plus il faut de place, sans quoi elle déborde sur la voisine. "
                + "Court : " + nodesep(court) + ", long : " + nodesep(long_));
    }

    @Test
    void diagrammeSansEtiquette_resteCompact() {
        String sans = PlantUmlAssert.documentPour(
            paire(UmlAssociation.builder("champ", "Cible").build()));
        String avec = PlantUmlAssert.documentPour(paire(
            UmlAssociation.builder("champ", "Cible").targetRole("unRôleAssezLong").build()));

        assertTrue(nodesep(sans) < nodesep(avec),
            "Sans la moindre étiquette, rien ne justifie d'étaler le diagramme.");
    }
}
