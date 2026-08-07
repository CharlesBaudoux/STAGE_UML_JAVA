package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// Les boîtes que le fichier de règles déclare, et qu'aucune classe Java ne porte.
//
// CE QUE CES TESTS PROTÈGENT
//
// C'est la seule partie de la bibliothèque qui puisse mentir : une boîte déclarée n'est vérifiée
// par aucun compilateur et ne suit aucun renommage. Le contrat est donc qu'elle se voie —
// stéréotype et contour pointillé —, et c'est ce que le dernier groupe de tests fige. Si cette
// marque disparaissait, le diagramme deviendrait indistinguable de celui d'un projet dont le code
// dirait vraiment ce qu'on dessine.
class EntitesDeclareesTest {

    private static final UmlEntity COMPTE =
        UmlEntity.builder("com.banque.Compte", "Compte").build();

    private static ImmutableList<UmlEntity> declarees(String... lignes) {
        return EntitesDeclarees.de(ImmutableSet.of(COMPTE),
            Regles.lues(ImmutableList.copyOf(lignes), "les règles du test"));
    }

    private static UmlEntity une(ImmutableList<UmlEntity> boites, String nom) {
        return boites.stream().filter(b -> b.name().equals(nom)).findFirst().orElseThrow();
    }

    @Test
    void sansDeclaration_aucuneBoiteNestAjoutee() {
        assertTrue(declarees("strategy public").isEmpty());
    }

    @Test
    void uneClasseDeclaree_devientUneBoite() {
        ImmutableList<UmlEntity> boites = declarees("declare class Animal");

        assertEquals(1, boites.size());
        assertEquals("Animal", boites.get(0).name());
        assertTrue(boites.get(0).declared(), "Elle doit se savoir déclarée.");
    }

    @Test
    void unChampDeclare_entreDansLaBoite() {
        UmlEntity animal = une(declarees("declare class Animal",
            "declare field Animal nom : String"), "Animal");

        assertEquals(1, animal.fields().size());
        assertEquals("nom", animal.fields().get(0).name());
        assertEquals("String", animal.fields().get(0).type());
    }

    @Test
    void unChampDeclare_nAPasDeVisibilite() {
        // Ce qui n'existe pas dans le code n'a pas d'encapsulation à montrer : inventer une
        // visibilité serait une affirmation de plus que rien ne garantit.
        UmlEntity animal = une(declarees("declare class Animal",
            "declare field Animal nom : String"), "Animal");

        assertEquals("", animal.fields().get(0).visibility());
    }

    @Test
    void extends_poseUnHeritage() {
        UmlEntity chien = une(
            declarees("declare class Animal", "declare class Chien extends Animal"), "Chien");

        assertEquals(1, chien.inheritances().size());
        assertEquals("Animal", chien.inheritances().get(0).targetQualifiedName());
    }

    @Test
    void uneClasseDeclaree_peutHeriterDUneClasseDuCode() {
        UmlEntity livret = une(declarees("declare class Livret extends Compte"), "Livret");

        assertEquals("com.banque.Compte", livret.inheritances().get(0).targetQualifiedName(),
            "La cible est résolue vers le nom complet de la classe réelle.");
    }

    @Test
    void uneAssociationDeclaree_relieDeuxBoites() {
        UmlEntity animal = une(declarees("declare class Animal",
            "declare association Animal -> Compte 0..*"), "Animal");

        assertEquals(1, animal.associations().size());
        assertEquals("com.banque.Compte", animal.associations().get(0).targetQualifiedName());
        assertEquals("0..*", animal.associations().get(0).targetCardinality());
    }

    @Test
    void uneAssociationDepuisUneClasseDuCode_estRefusee() {
        // Une entité réelle est déjà construite quand le fichier est lu : lui ajouter un lien
        // ici passerait par-dessus ce que son code déclare, sans que rien ne le montre.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> declarees("declare class Animal", "declare association Compte -> Animal"));

        assertTrue(e.getMessage().contains("must be a declared class"), e.getMessage());
    }

    @Test
    void unNomInconnu_estRefuse() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> declarees("declare class Chien extends Mammifere"));

        assertTrue(e.getMessage().contains("neither a class of the diagram"), e.getMessage());
    }

    @Test
    void unChampSurUneClasseNonDeclaree_estRefuse() {
        assertThrows(IllegalArgumentException.class,
            () -> declarees("declare field Animal nom : String"));
    }

    @Test
    void uneClasseDeclareeDeuxFois_estRefusee() {
        assertThrows(IllegalArgumentException.class,
            () -> declarees("declare class Animal", "declare class Animal"));
    }

    @Test
    void unDeclareMalEcrit_estRefuse() {
        assertThrows(IllegalArgumentException.class, () -> declarees("declare animal"));
        assertThrows(IllegalArgumentException.class, () -> declarees("declare class"));
        assertThrows(IllegalArgumentException.class,
            () -> declarees("declare class Animal", "declare field Animal nom"));
        assertThrows(IllegalArgumentException.class,
            () -> declarees("declare class A", "declare association A Compte"));
    }

    // --- La marque visuelle, qui est la contrepartie de tout ceci ---

    @Test
    void uneBoiteDeclaree_porteSonStereotype() {
        String source = PlantUmlAssert.documentPour(
            declarees("declare class Animal").toArray(new UmlEntity[0]));

        assertTrue(source.contains("<<declared>>"), source);
    }

    @Test
    void uneBoiteDeclaree_aUnContourPointille() {
        String source = PlantUmlAssert.documentPour(
            declarees("declare class Animal").toArray(new UmlEntity[0]));

        assertTrue(source.contains("#line.dashed"), source);
    }

    @Test
    void uneBoiteOrdinaire_nEnPorteAucune() {
        String source = PlantUmlAssert.documentPour(COMPTE);

        assertFalse(source.contains("<<declared>>"), source);
        assertFalse(source.contains("#line.dashed"), source);
    }

    @Test
    void leDessin_estAccepteParLeMoteur() {
        List<UmlEntity> toutes = new java.util.ArrayList<>(declarees("declare class Animal",
            "declare field Animal nom : String", "declare class Chien extends Animal",
            "declare association Animal -> Compte 0..*"));
        toutes.add(COMPTE);

        PlantUmlAssert.assertCompile(
            PlantUmlAssert.documentPour(toutes.toArray(new UmlEntity[0])));
    }
}
