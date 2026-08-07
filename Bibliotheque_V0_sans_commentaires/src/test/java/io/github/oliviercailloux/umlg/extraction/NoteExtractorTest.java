package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlNote;
import java.util.List;
import org.junit.jupiter.api.Test;

// Ce que l'extraction retient des @UMLNote.
//
// Le fil conducteur : une bulle ne se dessine qu'accrochée à quelque chose. Toutes les notes
// visant un membre que la boîte ne montre pas doivent disparaître ici, sans quoi le rendu
// demanderait à PlantUML d'accrocher une bulle à un membre qu'il ne connaît pas.
class NoteExtractorTest {

    private static List<String> textes(UmlEntity entity) {
        return entity.notes().stream().map(UmlNote::text).toList();
    }

    @UMLClass
    @UMLNote(text = "Coeur du domaine")
    @UMLNote(text = "Ne pas exposer hors du module", direction = UMLDirection.LEFT)
    static class Commande {}

    @Test
    void notesDeClasse_sontToutesRetenues() {
        UmlEntity entity = EntityExtractor.extract(Commande.class);

        assertEquals(List.of("Coeur du domaine", "Ne pas exposer hors du module"), textes(entity));
        assertTrue(entity.notes().stream().noneMatch(UmlNote::surMembre));
    }

    @Test
    void directionEcrite_estConservee() {
        UmlEntity entity = EntityExtractor.extract(Commande.class);

        assertEquals(UMLDirection.NONE, entity.notes().get(0).direction());
        assertEquals(UMLDirection.LEFT, entity.notes().get(1).direction());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Compte {

        @UMLNote(text = "Jamais negatif")
        public double solde;

        @UMLNote(text = "Invisible, donc perdue")
        private String motDePasse;

        @UMLNote(text = "Algorithme critique", direction = UMLDirection.RIGHT)
        public void verifier() {}
    }

    @Test
    void noteDeMembre_porteLeNomDuMembre() {
        UmlEntity entity = EntityExtractor.extract(Compte.class);

        UmlNote note = entity.notes().stream()
            .filter(n -> n.text().equals("Jamais negatif")).findFirst().orElseThrow();

        assertEquals("solde", note.anchor());
        assertTrue(note.surMembre());
    }

    @Test
    void noteDeMethode_porteLeNomDeLaMethode() {
        UmlEntity entity = EntityExtractor.extract(Compte.class);

        assertEquals("verifier", entity.notes().stream()
            .filter(n -> n.text().equals("Algorithme critique")).findFirst().orElseThrow()
            .anchor());
    }

    @Test
    void noteVisantUnMembreMasque_estEcartee() {
        UmlEntity entity = EntityExtractor.extract(Compte.class);

        assertTrue(textes(entity).stream().noneMatch(t -> t.contains("perdue")),
            "La note d'un membre masqué doit disparaître : " + textes(entity));
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Client {

        @UMLNote(text = "Visée par un lien, pas par une boîte")
        @UMLAssociation(target = Commande.class)
        private Commande commande;
    }

    @Test
    void noteSurUnAttributPromuEnLien_estEcartee() {
        UmlEntity entity = EntityExtractor.extract(Client.class);

        assertEquals(List.of(), textes(entity));
    }

    @UMLNote(text = "   ")
    static class NoteVide {}

    @Test
    void texteFaitDEspaces_estRefuseEnNommantLEmplacement() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> NoteExtractor.extraire(NoteVide.class, List.of(), List.of()));

        assertTrue(e.getMessage().contains("NoteVide"), e.getMessage());
        assertTrue(e.getMessage().contains("@UMLNote"), e.getMessage());
    }
}
