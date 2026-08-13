package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssociationDeriveeTest {

    @UMLClass
    static class Commande {}

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Client {

        @UMLDerived
        @UMLAssociation(target = Commande.class, targetRole = "encours",
            targetMultiplicity = "0..*")
        private List<Commande> commandesEnCours;

        @UMLAssociation(target = Commande.class, targetRole = "archives",
            targetMultiplicity = "0..*")
        private List<Commande> commandesArchivees;
    }

    private static UmlEntity client() {
        return EntityExtractor.extract(Client.class);
    }

    @Test
    void lienAnnote_estDitDerive() {
        assertTrue(client().associations().stream()
            .filter(a -> a.targetRole().equals("encours"))
            .findFirst().orElseThrow().derived());
    }

    @Test
    void lienVoisin_resteOrdinaire() {
        assertFalse(client().associations().stream()
            .filter(a -> a.targetRole().equals("archives"))
            .findFirst().orElseThrow().derived());
    }

    @Test
    void lAttributNeRevientPasDansLaBoite() {
        UmlEntity entity = client();

        assertTrue(entity.fields().stream()
            .noneMatch(f -> f.name().equals("commandesEnCours")),
            "L'attribut porteur du lien ne doit pas figurer dans la boîte : " + entity.fields());
    }
}
