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

class PlantUmlNoteTest {

    private static UmlEntity boite(List<UmlNote> notes) {
        return UmlEntity.builder("com.exemple.Client", "Client")
            .fields(List.of(new UmlField("solde", "double", "-", false)))
            .notes(notes).build();
    }

    private static String source(UmlNote... notes) {
        return PlantUmlAssert.documentPour(boite(List.of(notes)));
    }

    private static List<String> lignesDeNote(String source) {
        return source.lines().filter(l -> l.startsWith("note ")).toList();
    }

    private static List<String> blocDeNote(String source) {
        return blocDeNote(source, 3);
    }

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
        String source = source(UmlNote.surClasse("Texte", direction));

        assertEquals(List.of("note " + attendu + " of Client : Texte"), lignesDeNote(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurMembre_seRattacheAuMembre() {
        String source = source(new UmlNote("Jamais negatif", UMLDirection.RIGHT, "solde"));

        assertEquals(List.of("note right of Client::solde", "&#74;amais negatif", "end note"),
            blocDeNote(source));
        PlantUmlAssert.assertCompile(source);
    }

    @ParameterizedTest
    @CsvSource({"NONE,right", "RIGHT,right", "LEFT,left", "UP,right", "DOWN,right"})
    void directionSurMembre_seRabatSurUnCoteQuePlantUmlConnait(UMLDirection direction,
        String attendu) {
        String source = source(new UmlNote("Texte", direction, "solde"));

        assertEquals("note " + attendu + " of Client::solde", blocDeNote(source).get(0), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurMembre_texteMultiligne_utiliseDeVraisSauts() {
        String source = source(new UmlNote("Premiere\nSeconde", UMLDirection.RIGHT, "solde"));

        assertEquals(
            List.of("note right of Client::solde", "&#80;remiere", "&#83;econde", "end note"),
            blocDeNote(source, 4), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurMembre_texteMultiligne_estLuSurPlusieursLignes() throws Exception {
        String texte = PlantUmlAssert.texteRendu(
            boite(List.of(new UmlNote("Premiere\nSeconde", UMLDirection.RIGHT, "solde"))));

        assertFalse(texte.contains("\\n"), "Le saut de ligne s'affiche en toutes lettres :\n"
            + texte);
        assertTrue(texte.contains("Premiere"), texte);
        assertTrue(texte.contains("Seconde"), texte);
    }

    @Test
    void noteSurMembre_ligneInterieureImitantLeTerminateur_neRefermeRien() {
        String source =
            source(new UmlNote("Anodin\nend note\nclass Pirate", UMLDirection.RIGHT, "solde"));

        assertEquals(1, PlantUmlAssert.nombreDeDiagrammes(source), source);
        assertFalse(PlantUmlAssert.aliasDeclares(source).contains("Pirate"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurMembre_texteImitantLeTerminateur_neRefermeRien() {
        String source = source(new UmlNote("end note", UMLDirection.RIGHT, "solde"));

        assertEquals(List.of("note right of Client::solde", "&#101;nd note", "end note"),
            blocDeNote(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void noteSurMembre_lecteurVoitLeTexteImitant() throws Exception {
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

    @Test
    void texteMultiligne_resteSurUneSeuleLigne() {
        String source = source(UmlNote.surClasse("Premiere\nSeconde", UMLDirection.RIGHT));

        assertEquals(1, lignesDeNote(source).size(), source);
        assertTrue(lignesDeNote(source).get(0).contains("\\n"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void texteContenantEndNote_neRefermeRien() {
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
