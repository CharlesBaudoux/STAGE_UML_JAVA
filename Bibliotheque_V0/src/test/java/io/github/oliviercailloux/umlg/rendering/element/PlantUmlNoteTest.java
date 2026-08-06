package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlNote;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Le rendu des bulles de commentaire.
//
// Deux préoccupations : le bon placement (PlantUML n'accepte pas de note sans côté, et son
// vocabulaire n'est pas celui de l'énuméré), et la sûreté du texte — c'est le seul endroit du
// diagramme où l'utilisateur écrit une phrase entière.
class PlantUmlNoteTest {

    private static UmlEntity boite(List<UmlNote> notes) {
        return UmlEntity.builder("com.exemple.Client", "Client")
            .fields(List.of(new UmlField("solde", "double", "-", false)))
            .notes(notes).build();
    }

    private static String source(UmlNote... notes) {
        return PlantUmlAssert.documentPour(boite(List.of(notes)));
    }

    // Les lignes qui ouvrent une note.
    private static List<String> lignesDeNote(String source) {
        return source.lines().filter(l -> l.startsWith("note ")).toList();
    }

    // Le bloc complet d'une note de membre : l'ouverture, le texte, le terminateur.
    private static List<String> blocDeNote(String source) {
        return blocDeNote(source, 3);
    }

    // La même chose, quand le texte occupe plus d'une ligne : le bloc en compte alors autant en
    // plus.
    private static List<String> blocDeNote(String source, int hauteur) {
        List<String> lignes = source.lines().toList();
        int debut = lignes.indexOf(lignes.stream().filter(l -> l.startsWith("note "))
            .findFirst().orElseThrow());
        return lignes.subList(debut, debut + hauteur);
    }

    @Test
    void noteSurClasse_seRattacheALaBoite() {
        String source = source(UmlNote.surClasse("Dette technique assumee", UMLDirection.RIGHT));

        assertEquals(List.of("note right of Client : Dette technique assumee"),
            lignesDeNote(source));
        PlantUmlAssert.assertCompile(source);
    }

    @ParameterizedTest
    @CsvSource({"NONE,right", "RIGHT,right", "LEFT,left", "UP,top", "DOWN,bottom"})
    void direction_estTraduiteDansLeVocabulaireDePlantUml(UMLDirection direction, String attendu) {
        // UP et DOWN ne se disent pas « up » et « down » : une simple mise en minuscules
        // produirait une syntaxe que PlantUML refuse. NONE doit choisir un côté, faute de quoi
        // la note n'aurait nulle part où se poser.
        String source = source(UmlNote.surClasse("Texte", direction));

        assertEquals(List.of("note " + attendu + " of Client : Texte"), lignesDeNote(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurMembre_seRattacheAuMembre() {
        // PlantUML refuse la forme d'une seule ligne pour un membre : seul le bloc fonctionne.
        // Le texte y est écrit avec son premier caractère encodé, ce qui le rend incapable
        // d'imiter le terminateur.
        String source = source(new UmlNote("Jamais negatif", UMLDirection.RIGHT, "solde"));

        assertEquals(List.of("note right of Client::solde", "&#74;amais negatif", "end note"),
            blocDeNote(source));
        PlantUmlAssert.assertCompile(source);
    }

    @ParameterizedTest
    @CsvSource({"NONE,right", "RIGHT,right", "LEFT,left", "UP,right", "DOWN,right"})
    void directionSurMembre_seRabatSurUnCoteQuePlantUmlConnait(UMLDirection direction,
        String attendu) {
        // PlantUML ne place une note de membre qu'à gauche ou à droite : « note top of A::m »
        // n'est pas dégradé, il est refusé, et son refus emporte le document entier. UP et DOWN
        // doivent donc se rabattre, sans quoi une seule annotation mal orientée remplacerait le
        // diagramme par une image d'erreur.
        String source = source(new UmlNote("Texte", direction, "solde"));

        assertEquals("note " + attendu + " of Client::solde", blocDeNote(source).get(0), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurMembre_texteMultiligne_utiliseDeVraisSauts() {
        // Un bloc n'interprète pas la séquence « \n » : elle s'y afficherait en toutes lettres.
        // Ce sont donc de vrais retours à la ligne qu'il faut écrire — l'inverse exact de la
        // forme sur une seule ligne.
        String source = source(new UmlNote("Premiere\nSeconde", UMLDirection.RIGHT, "solde"));

        assertEquals(
            List.of("note right of Client::solde", "&#80;remiere", "&#83;econde", "end note"),
            blocDeNote(source, 4), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurMembre_texteMultiligne_estLuSurPlusieursLignes() throws Exception {
        // Le seul niveau qui prouve la correction : ce que le lecteur voit. Un « \n » littéral
        // apparaîtrait ici au milieu de la phrase.
        String texte = PlantUmlAssert.texteRendu(
            boite(List.of(new UmlNote("Premiere\nSeconde", UMLDirection.RIGHT, "solde"))));

        assertFalse(texte.contains("\\n"), "Le saut de ligne s'affiche en toutes lettres :\n"
            + texte);
        assertTrue(texte.contains("Premiere"), texte);
        assertTrue(texte.contains("Seconde"), texte);
    }

    @Test
    void noteSurMembre_ligneInterieureImitantLeTerminateur_neRefermeRien() {
        // Le découpage en lignes crée une brèche que la forme d'un seul tenant n'avait pas :
        // n'importe quelle ligne du texte devient une ligne du bloc. La protection porte donc
        // sur toutes, et non plus sur la première seule.
        String source =
            source(new UmlNote("Anodin\nend note\nclass Pirate", UMLDirection.RIGHT, "solde"));

        assertEquals(1, PlantUmlAssert.nombreDeDiagrammes(source), source);
        assertFalse(PlantUmlAssert.aliasDeclares(source).contains("Pirate"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurMembre_texteImitantLeTerminateur_neRefermeRien() {
        // Le cas qui justifie l'encodage : sans lui, cette note se refermerait sur elle-même et
        // PlantUML rejetterait le document entier.
        String source = source(new UmlNote("end note", UMLDirection.RIGHT, "solde"));

        assertEquals(List.of("note right of Client::solde", "&#101;nd note", "end note"),
            blocDeNote(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurMembre_lecteurVoitLeTexteImitant() throws Exception {
        // L'encodage ne doit pas se voir : le lecteur lit bien « end note ».
        String texte = PlantUmlAssert.texteRendu(
            boite(List.of(new UmlNote("end note", UMLDirection.RIGHT, "solde"))));

        assertTrue(texte.contains("end note"), texte);
    }

    @Test
    void notesMultiples_sortentToutes() {
        String source = source(UmlNote.surClasse("Premiere", UMLDirection.UP),
            UmlNote.surClasse("Seconde", UMLDirection.DOWN));

        assertEquals(2, lignesDeNote(source).size(), source);
        PlantUmlAssert.assertCompile(source);
    }

    // --- La sûreté du texte ---

    @Test
    void texteMultiligne_resteSurUneSeuleLigne() {
        // Le retour à la ligne devient la séquence « \n », que PlantUML affiche comme un saut de
        // ligne. Ce qui compte ici est qu'aucune ligne nouvelle n'apparaisse dans la source : le
        // reste du texte serait alors lu comme de la syntaxe.
        String source = source(UmlNote.surClasse("Premiere\nSeconde", UMLDirection.RIGHT));

        assertEquals(1, lignesDeNote(source).size(), source);
        assertTrue(lignesDeNote(source).get(0).contains("\\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void texteContenantEndNote_neRefermeRien() {
        // La raison d'être de la forme sur une seule ligne : dans la forme en bloc, cette phrase
        // refermerait la bulle et la suite s'écrirait en syntaxe PlantUML.
        String source =
            source(UmlNote.surClasse("end note\n@enduml\nclass Pirate", UMLDirection.RIGHT));

        assertEquals(1, PlantUmlAssert.nombreDeDiagrammes(source),
            "Le texte a decoupe le document :\n" + source);
        assertFalse(PlantUmlAssert.aliasDeclares(source).contains("Pirate"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void lecteurVoitLeTexte() throws Exception {
        String texte = PlantUmlAssert.texteRendu(
            boite(List.of(UmlNote.surClasse("Algorithme critique", UMLDirection.RIGHT))));

        assertTrue(texte.contains("Algorithme critique"), texte);
    }
}
