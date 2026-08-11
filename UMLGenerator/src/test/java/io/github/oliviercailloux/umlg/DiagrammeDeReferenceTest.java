package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

// Le test de référence : un diagramme entier, comparé à un fichier écrit à la main.
//
// CE QU'IL PROUVE, ET CE QU'IL NE PROUVE PAS
//
// Les autres tests vérifient des fragments — « la ligne de l'attribut contient ceci ». Aucun ne
// regarde le document dans son ensemble, si bien qu'une régression globale leur échapperait :
// une passe de rendu appelée dans le mauvais ordre, une ligne vide de trop, une note écrite
// avant sa boîte, un bloc de style modifié par mégarde.
//
// Ce test-ci fixe le tout. Il dit « la sortie n'a pas changé », jamais « la sortie est
// correcte » : cette seconde question reste celle des tests ciblés, qui savent pourquoi chaque
// morceau doit être ce qu'il est. Les deux sont complémentaires, et il n'en faut qu'un de cette
// sorte — une collection de fichiers de référence devient un impôt, chaque évolution légitime en
// cassant dix d'un coup.
//
// QUAND IL ÉCHOUE
//
// Deux cas, et il faut les distinguer avant de toucher quoi que ce soit. Soit la bibliothèque a
// régressé, et c'est elle qu'on corrige. Soit le changement était voulu, et on met le fichier de
// référence à jour — après avoir lu l'écart ligne à ligne, ce qui est précisément le moment où
// l'on vérifie que le changement fait bien ce qu'on croyait.
//
// SUR L'ORDRE, QUI EST TOUT LE SUJET D'UN TEL TEST
//
// Comparer un document entier, c'est aussi figer l'ordre de ses lignes. Trois ordres entrent en
// jeu, et ils n'ont pas la même solidité.
//
// Celui des boîtes est décidé ici, entité par entité. Le document ne dépend donc pas de l'ordre
// dans lequel le système de fichiers rend les fichiers d'un répertoire, qu'aucune norme ne
// garantit — c'est la raison pour laquelle ce test énumère au lieu de scanner.
//
// Celui des relations vient d'EntityExtractor, qui appelle ses extracteurs dans un ordre écrit :
// associations, puis dépendances, puis héritages. D'où un « extends » écrit après le lien
// d'association de la même classe, ce qui surprend à la lecture mais est parfaitement stable.
//
// Celui des membres, enfin, est celui que rend getDeclaredFields et getDeclaredMethods. La
// spécification de Java ne le définit pas, et la mesure le confirme : ce n'est pas l'ordre de
// déclaration du source. Il est stable pour une machine virtuelle donnée, pas garanti d'une
// implémentation à l'autre. Si ce test échoue un jour sur le seul ordre des membres, ce n'est
// donc pas une régression de la bibliothèque : c'est ce point qu'il faudra traiter, en triant
// les membres à l'extraction.
class DiagrammeDeReferenceTest {

    private static final String FICHIER_DE_REFERENCE = "/diagramme-de-reference.puml";

    // Les règles du projet cobaye : ce qu'il tient pour du bruit, nommé par des motifs.
    //
    // Rien n'est masqué d'office, et ce test est le meilleur endroit pour le vérifier : trois
    // motifs suffisent à écarter tout ce que ce cobaye-là compte de plomberie — l'accesseur
    // getNom(), et les deux méthodes que javac ajoute à l'énumération Statut. Le document attendu
    // n'a pas changé d'une ligne le jour où le fichier de règles a cessé de connaître des
    // « familles » et n'a plus lu que des noms.
    private static final Regles REGLES = Regles.lues("""
        { "hide": ["get*", "values", "valueOf"] }
        """, "les règles du projet de référence");

    // Les entités du diagramme, dans l'ordre où elles seront déclarées : les cibles avant ce qui
    // les vise, ce qui rend le fichier engendré lisible de haut en bas.
    private static ImmutableList<UmlEntity> entites() {
        return ImmutableList.of(
            EntityExtractor.extract(ProjetDeReference.Identifiable.class, REGLES),
            EntityExtractor.extract(ProjetDeReference.Personne.class, REGLES),
            EntityExtractor.extract(ProjetDeReference.Statut.class, REGLES),
            EntityExtractor.extract(ProjetDeReference.Commande.class, REGLES),
            EntityExtractor.extract(ProjetDeReference.ServiceBancaire.class, REGLES),
            EntityExtractor.extract(ProjetDeReference.ClientFacture.class, REGLES));
    }

    private static String documentEngendre() {
        return PlantUmlAssert.documentPour(entites().toArray(new UmlEntity[0]));
    }

    private static String reference() throws Exception {
        try (InputStream flux = DiagrammeDeReferenceTest.class
            .getResourceAsStream(FICHIER_DE_REFERENCE)) {

            assertNotNull(flux, "Fichier de référence introuvable : " + FICHIER_DE_REFERENCE);
            return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void leDocumentEngendre_estCeluiEcritALaMain() throws Exception {
        // assertEquals sur la chaîne entière : c'est ce qui donne un écart lisible ligne à ligne
        // dans le rapport de JUnit comme dans l'IDE.
        assertEquals(reference(), documentEngendre());
    }

    @Test
    void leDocumentEngendre_estAcceptibleParPlantUml() {
        // La référence pourrait être stable et fausse. Cette seconde assertion la tient valide :
        // le texte doit être un diagramme que PlantUML accepte de dessiner.
        PlantUmlAssert.assertCompile(documentEngendre());
    }

    @Test
    void leDocumentEngendre_neFormeQuunSeulDiagramme() {
        // Le cobaye contient des textes libres — notes, contraintes, libellés. Aucun ne doit
        // pouvoir découper le document en deux.
        assertEquals(1, PlantUmlAssert.nombreDeDiagrammes(documentEngendre()));
    }
}
