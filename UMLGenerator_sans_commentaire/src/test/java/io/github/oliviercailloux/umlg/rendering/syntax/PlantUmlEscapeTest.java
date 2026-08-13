package io.github.oliviercailloux.umlg.rendering.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PlantUmlEscapeTest {

    private static UmlEntity nommee(String nom) {
        return new UmlEntity(nom, "", "", UMLVisibility.PUBLIC, List.of(), List.of());
    }

    private static UmlEntity stereotypee(String stereotype) {
        return new UmlEntity("E", stereotype, "", UMLVisibility.PUBLIC, List.of(), List.of());
    }

    static List<String> nomsHostiles() {
        return List.of(
            "Nom\"Injecte",
            "\" as X\nclass \"Intruse",
            "<img:/etc/hostname>",
            "<size:40>ENORME</size>",
            "<b>gras</b>",
            "&#60;img:/etc/hostname&#62;",
            "C:\\Users\\moi",
            "fin\\",
            "ligne1\nligne2",
            "ligne1\r\nligne2",
            "@enduml\n@startuml\nclass Intruse",
            "}\nclass Intruse {",
            "Compte [[http://ailleurs.example]]",
            "[[http://ailleurs.example{infobulle} etiquette]]",
            "**gras**",
            "//pente//",
            "~~barre~~",
            "__souligne__",
            "--barre--",
            "avec nul \u0000 et bip \u0007");
    }


    @ParameterizedTest
    @MethodSource("nomsHostiles")
    void nomHostile_produitUnDocumentValideDUneSeuleClasse(String nom) {
        String source = PlantUmlAssert.documentPour(nommee(nom));

        PlantUmlAssert.assertCompile(source);
        assertEquals(1, PlantUmlAssert.nomsDeclares(source).size(),
            "Le nom hostile a déclaré une classe supplémentaire :\n" + source);
        assertEquals(1, PlantUmlAssert.nombreDeDiagrammes(source),
            "Un seul diagramme doit être ouvert :\n" + source);
    }

    @ParameterizedTest
    @MethodSource("nomsHostiles")
    void nomHostile_neLaissePasserAucuneBaliseCreole(String nom) {
        String source = PlantUmlAssert.documentPour(nommee(nom));

        assertFalse(source.contains("<img"), "Balise <img> non neutralisée :\n" + source);
        assertFalse(source.contains("<size"), "Balise <size> non neutralisée :\n" + source);
        assertFalse(source.contains("<b>"), "Balise <b> non neutralisée :\n" + source);
    }

    @ParameterizedTest
    @MethodSource("nomsHostiles")
    void nomHostile_neCasseJamaisLaLigneDeDeclaration(String nom) {
        String source = PlantUmlAssert.documentPour(nommee(nom));
        String declaration = source.lines()
            .filter(l -> l.startsWith("class "))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Aucune déclaration :\n" + source));

        assertEquals(2, declaration.chars().filter(c -> c == '"').count(),
            "Le nom a introduit un guillemet dans la déclaration : " + declaration);
    }


    @ParameterizedTest
    @ValueSource(strings = {
        "Compte (bancaire)", "L'Utilisateur", "Client<T>", "Nom\"Injecte", "A&B",
        "&#60;b&#62;", "C:\\Users", "Reservation n°42",
        "**gras**", "//pente//", "~~barre~~", "__souligne__", "--barre--",
        "Compte [[http://ailleurs.example]]",
    })
    void nomQuelconque_estAfficheTelQuel(String nom) throws Exception {
        String rendu = PlantUmlAssert.texteRendu(nommee(nom));

        assertTrue(rendu.contains(nom),
            "Le nom « " + nom + " » n'est pas rendu fidèlement. Texte dessiné :\n" + rendu);
    }

    @Test
    void entiteEchappee_nEstPasReinterpretee() throws Exception {
        UmlEntity e = nommee("&#60;img:/etc/hostname&#62;");

        String source = PlantUmlAssert.documentPour(e);
        assertTrue(source.contains("&#38;#60;"),
            "L'esperluette doit être encodée en premier : " + source);

        String rendu = PlantUmlAssert.texteRendu(e);
        assertTrue(rendu.contains("&#60;img:/etc/hostname&#62;"),
            "Le texte doit s'afficher littéralement, pas être décodé en balise :\n" + rendu);
    }

    @Test
    void antislash_estDoubleEtNonEncode() throws Exception {
        String source = PlantUmlAssert.documentPour(nommee("C:\\Users"));

        assertFalse(source.contains("&#92;"), source);
        assertTrue(source.contains("C:\\\\Users"), source);
        assertTrue(PlantUmlAssert.texteRendu(nommee("C:\\Users")).contains("C:\\Users"));
    }

    @Test
    void sautDeLigne_devientUnPassageALaLigne() {
        String source = PlantUmlAssert.documentPour(nommee("ligne1\nligne2"));

        assertEquals(1, source.lines().filter(l -> l.startsWith("class ")).count());
        assertTrue(source.contains("ligne1\\nligne2"),
            "Le saut de ligne doit devenir un « \\n » PlantUML, pas disparaître :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void retourChariotWindows_neProduitQuUnSeulPassageALaLigne() {
        String source = PlantUmlAssert.documentPour(nommee("ligne1\r\nligne2"));

        assertTrue(source.contains("ligne1\\nligne2"), source);
        assertFalse(source.contains("\\n\\n"), source);
    }


    @Test
    void stereotypeAvecChevrons_neTronquePasLaSuite() throws Exception {
        UmlEntity e = stereotypee("A>>B");

        PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(e));
        assertTrue(PlantUmlAssert.texteRendu(e).contains("A>>B"),
            "Un « >> » interne tronquait silencieusement le stéréotype.");
    }

    @Test
    void stereotypeAvecEsperluette_estAffichee() throws Exception {
        assertTrue(PlantUmlAssert.texteRendu(stereotypee("R&D")).contains("R&D"));
    }

    @Test
    void stereotypeAvecSautDeLigne_resteSurUneLigne() {
        String source = PlantUmlAssert.documentPour(stereotypee("a\nb"));

        assertTrue(source.contains("<<a b>>"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void stereotype_nEncodePasLAntislash() {
        assertEquals("a\\b", PlantUmlEscaper.stereotype("a\\b"));
        assertEquals("a\\\\b", PlantUmlEscaper.label("a\\b"));
    }


    @Test
    void membreHostile_neCassePasLeCompartiment() {
        UmlEntity e = new UmlEntity("E", "", "", UMLVisibility.PUBLIC,
            List.of(new UmlField("champ\n}\nclass Intruse {", "<img:/etc/hostname>", "-", false)),
            List.of(new UmlMethod("m\"ethode", "void", "+", false, List.of("A>B"))));

        String source = PlantUmlAssert.documentPour(e);

        PlantUmlAssert.assertCompile(source);
        assertEquals(1, PlantUmlAssert.nomsDeclares(source).size(), source);
        assertFalse(source.contains("<img"), source);
    }


    @Test
    void couleurNommee_estAcceptee() {
        String source = PlantUmlAssert.documentPour(
            new UmlEntity("E", "", "LightBlue", UMLVisibility.PUBLIC, List.of(), List.of()));

        assertTrue(source.contains("#LightBlue"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void couleurInconnue_echoueFranchement() {
        UmlEntity e = new UmlEntity("E", "", "PasUneCouleur", UMLVisibility.PUBLIC,
            List.of(), List.of());

        IllegalArgumentException erreur =
            assertThrows(IllegalArgumentException.class, () -> PlantUmlAssert.documentPour(e));
        assertTrue(erreur.getMessage().contains("PasUneCouleur"), erreur.getMessage());
    }

    @Test
    void couleurHexadecimale_resteAcceptee() {
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(
            new UmlEntity("E", "", "AABBCC", UMLVisibility.PUBLIC, List.of(), List.of())));
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(
            new UmlEntity("E", "", "#ABC", UMLVisibility.PUBLIC, List.of(), List.of())));
    }


    @Test
    void encodeur_nUtiliseQueDesEntitesNumeriques() {
        assertEquals("&#60;&#62;&#38;&#34;", PlantUmlEscaper.label("<>&\""));
    }

    @Test
    void encodeur_laisseIntactUnTexteOrdinaire() {
        String texte = "Compte (bancaire) n°42 - L'Utilisateur";

        assertEquals(texte, PlantUmlEscaper.label(texte));
        assertEquals(texte, PlantUmlEscaper.stereotype(texte));
    }

    @Test
    void encodeur_supprimeLesCaracteresDeControle() {
        assertEquals("ab", PlantUmlEscaper.label("a\u0000b"));
        assertEquals("ab", PlantUmlEscaper.stereotype("a\u0007b"));
    }


    @Test
    void unNomPorteurDunLien_neProduitAucunHref() throws Exception {
        String svg = PlantUmlAssert.svg(nommee("Compte [[http://ailleurs.example]]"));

        assertFalse(svg.contains("ailleurs.example\""),
            "Le nom a engendré un lien dans le SVG :\n" + svg);
        assertFalse(svg.contains("<a href="), "Le nom a engendré un lien dans le SVG :\n" + svg);
    }

    @Test
    void encodeur_neCasseQueLaPaire_etLaisseLeCaractereIsole() {
        assertEquals("nom_de_champ", PlantUmlEscaper.label("nom_de_champ"));
        assertEquals("a * b", PlantUmlEscaper.label("a * b"));
        assertEquals("et/ou", PlantUmlEscaper.label("et/ou"));

        assertEquals("&#95;_souligne&#95;_", PlantUmlEscaper.label("__souligne__"));
        assertEquals("&#91;[lien]]", PlantUmlEscaper.label("[[lien]]"));
    }


    @Test
    void stereotypeVideApresEchappement_nEcritPasDeChevronsVides() {
        String source = PlantUmlAssert.documentPour(stereotypee("\1\2"));

        assertFalse(source.contains("<<>>"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void nomVideApresEchappement_seVoitDansLaSourceEngendree() {
        assertEquals("", PlantUmlEscaper.label("\1\2"));
        assertEquals("", PlantUmlEscaper.stereotype("\1\2"));
    }

    @Test
    void nomVideApresEchappement_estRefuseParLeRendu() {
        var refus = assertThrows(IllegalArgumentException.class,
            () -> PlantUmlAssert.documentPour(nommee("\1\2")));

        assertTrue(refus.getMessage().contains("invisible characters only"), refus.getMessage());
    }

    @Test
    void encodeur_appliqueDeuxFois_neProduitToujoursPasDeBalise() {
        String deuxFois = PlantUmlEscaper.label(PlantUmlEscaper.label("<img:/etc/hostname>"));

        assertFalse(deuxFois.contains("<"), deuxFois);
    }
}
