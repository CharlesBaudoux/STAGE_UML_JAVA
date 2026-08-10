package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Vérifie les gardes d'UmlAssociation, et surtout la frontière entre les deux régimes de
// validation : strict pour les multiplicités (vocabulaire fermé), minimal pour les textes lus
// par un humain.
class UmlAssociationTest {

    // --- Multiplicités : vocabulaire fermé ---

    // Aucune assertion : le test réussit si la construction ne lève rien. C'est suffisant, la
    // question posée étant « cette valeur est-elle acceptée ? ».
    @ParameterizedTest
    @ValueSource(strings = {"", "1", "0", "*", "0..1", "1..*", "0..*", "2..7", "3..3", "12"})
    void multipliciteBienFormee_estAcceptee(String cardinalite) {
        UmlAssociation.builder("champ", "Cible")
            .sourceCardinality(cardinalite)
            .targetCardinality(cardinalite)
            .build();
    }

    // La liste couvre les fautes usuelles : lettre isolée, intervalle tronqué, bornes inversées,
    // espace parasite, décimale — et « 0..*\nclass Intruse », qui tenterait d'injecter une ligne
    // dans le fichier PlantUML.
    @ParameterizedTest
    @ValueSource(strings = {
        "n", "0..x", "1..", "..*", "*..1", "0...1", "1..2..3", "-1", "1 ", " 1", "0..*extra",
        "0..*\nclass Intruse", "5..2", "1.2",
    })
    void multipliciteMalFormeeOuIncoherente_estRefusee(String cardinalite) {
        // « 5..2 » est le cas intéressant : irréprochable syntaxiquement, vide de sens. Une
        // vérification purement lexicale le laisserait passer, et le diagramme afficherait
        // sans broncher une multiplicité impossible.
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("champ", "Cible").targetCardinality(cardinalite).build());
    }

    // --- Textes libres : contrainte minimale ---

    @ParameterizedTest
    @ValueSource(strings = {"rôle (principal)", "n°42", "L'acheteur", "a<b>c", "\"guillemets\""})
    void texteLibreQuelconque_estAccepte(String texte) {
        // Un rôle ou un label sont destinés à être lus par un humain : interdire les caractères
        // qui gênent le moteur de rendu placerait le défaut au mauvais endroit. Leur sûreté est
        // obtenue par encodage à la sortie.
        UmlAssociation.builder("champ", "Cible")
            .sourceRole(texte).targetRole(texte).label(texte).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void texteLibreBlanc_estRefuse(String texte) {
        // Le vide déguisé est la seule faute : un texte affiché doit désigner quelque chose.
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("champ", "Cible").targetRole(texte).build());
        assertThrows(IllegalArgumentException.class,
            () -> UmlAssociation.builder("champ", "Cible").label(texte).build());
    }

    @Test
    void valeurNulle_estRefusee() {
        // Les deux premiers échouent dès le setter (checkNotNull y est appelé), le troisième dès
        // la création du builder : aucun n'attend build(). Un null se signale au plus tôt.
        assertThrows(NullPointerException.class,
            () -> UmlAssociation.builder("champ", "Cible").label(null));
        assertThrows(NullPointerException.class,
            () -> UmlAssociation.builder("champ", "Cible").type(null));
        assertThrows(NullPointerException.class, () -> UmlAssociation.builder("champ", null));
    }
}
