package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// Où atterrissent les @UMLConstraint.
//
// Trois destinations selon ce que l'annotation vise, et la troisième est une règle d'arbitrage :
// sur un attribut porteur de @UMLAssociation, la contrainte suit le lien, pas l'attribut.
class ConstraintExtractorTest {

    private static Optional<UmlField> champ(UmlEntity e, String nom) {
        return e.fields().stream().filter(f -> f.name().equals(nom)).findFirst();
    }

    @UMLClass
    static class Commande {}

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    @UMLConstraint(rule = "solde >= decouvertAutorise")
    @UMLConstraint(rule = "titulaire majeur")
    static class CompteBancaire {

        @UMLConstraint(rule = "toujours positif")
        private double solde;

        private double decouvertAutorise;

        @UMLConstraint(rule = "subset")
        @UMLAssociation(target = Commande.class, targetRole = "achats",
            targetCardinality = "0..*")
        private List<Commande> commandes;
    }

    private static UmlEntity compte() {
        return EntityExtractor.extract(CompteBancaire.class);
    }

    @Test
    void contraintesDeClasse_sontToutesRetenues() {
        assertEquals(List.of("solde >= decouvertAutorise", "titulaire majeur"),
            compte().constraints());
    }

    @Test
    void contrainteDAttribut_suitLAttribut() {
        assertEquals(List.of("toujours positif"),
            champ(compte(), "solde").orElseThrow().constraints());
    }

    @Test
    void attributVoisin_resteSansContrainte() {
        assertEquals(List.of(), champ(compte(), "decouvertAutorise").orElseThrow().constraints());
    }

    @Test
    void contrainteSurUnAttributPromuEnLien_suitLeLien() {
        assertEquals(List.of("subset"), compte().associations().get(0).constraints());
    }

    static class RegleVide {

        @UMLConstraint(rule = "  ")
        private double solde;
    }

    @Test
    void regleFaiteDEspaces_estRefuseeEnNommantLEmplacement() throws Exception {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ConstraintExtractor.de(RegleVide.class.getDeclaredField("solde"),
                "RegleVide.solde"));

        assertTrue(e.getMessage().contains("RegleVide.solde"), e.getMessage());
        assertTrue(e.getMessage().contains("@UMLConstraint"), e.getMessage());
    }
}
