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

    private static final Pattern TEXTE_SVG =
        Pattern.compile("<text[^>]*fill=\"(#[0-9A-Fa-f]{6})\"([^>]*)>([^<]*)</text>");

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

    private static String decode(String fragment) {
        Matcher m = Pattern.compile("&#(\\d+);").matcher(fragment);
        StringBuilder sortie = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sortie,
                Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
        }
        return m.appendTail(sortie).toString();
    }

    private static String apparence(String svg, String contenu) {
        Matcher m = TEXTE_SVG.matcher(svg);
        while (m.find()) {
            if (decode(m.group(3)).strip().equals(contenu)) {
                return m.group(1) + (m.group(2).contains("italic") ? " italic" : " droit");
            }
        }
        throw new AssertionError("Texte « " + contenu + " » absent du rendu :\n" + svg);
    }

    @Test
    void nomDuLien_etCardinalite_neSeRessemblentPas() throws Exception {
        String svg = PlantUmlAssert.svg(paire(UmlAssociation.builder("champ", "Cible")
            .targetCardinality("0..*").label("détient").build()));

        assertNotEquals(apparence(svg, "0..*"), apparence(svg, "détient"),
            "Le nom du lien et la cardinalité doivent se distinguer au premier coup d'œil.");
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
        String svg = PlantUmlAssert.svg(paire(UmlAssociation.builder("champ", "Cible")
            .label("lit//écrit").build()));

        assertTrue(decode(svg).contains("lit//écrit"),
            () -> "Le texte doit s'afficher tel quel, « // » compris :\n" + svg);
    }

    @Test
    void etiquetteLongue_ecarteDavantageLesBoites() {
        String court = PlantUmlAssert.documentPour(paire(
            UmlAssociation.builder("champ", "Cible").targetCardinality("1").build()));
        String long_ = PlantUmlAssert.documentPour(paire(
            UmlAssociation.builder("champ", "Cible")
                .targetRole("comptesDuTitulaire").targetCardinality("1..*")
                .ordered(true).build()));

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
