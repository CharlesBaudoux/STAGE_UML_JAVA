package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// Ce que @UMLDerived fait entrer dans le modèle.
//
// L'annotation ne dit rien d'autre que « cet élément se calcule ». Toute la question est donc
// qu'elle arrive intacte jusqu'au modèle, et qu'elle ne déborde pas sur les membres voisins.
class DerivationTest {

    private static Optional<UmlField> champ(UmlEntity e, String nom) {
        return e.fields().stream().filter(f -> f.name().equals(nom)).findFirst();
    }

    private static Optional<UmlMethod> methode(UmlEntity e, String nom) {
        return e.methods().stream().filter(m -> m.name().equals(nom)).findFirst();
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Personne {

        private LocalDate dateNaissance;

        @UMLDerived
        private int age;

        @UMLDerived
        public String resumer() {
            return "";
        }

        public void deplacer() {}
    }

    @Test
    void attributAnnote_estDitDerive() {
        UmlEntity entity = EntityExtractor.extract(Personne.class);

        assertTrue(champ(entity, "age").orElseThrow().derived());
    }

    @Test
    void attributVoisin_resteStocke() {
        UmlEntity entity = EntityExtractor.extract(Personne.class);

        assertFalse(champ(entity, "dateNaissance").orElseThrow().derived());
    }

    @Test
    void methodeAnnotee_estDiteDerivee() {
        UmlEntity entity = EntityExtractor.extract(Personne.class);

        assertTrue(methode(entity, "resumer").orElseThrow().derived());
        assertFalse(methode(entity, "deplacer").orElseThrow().derived());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Panier {

        @UMLDerived
        @UMLInclude
        private double total;
    }

    @Test
    void derivationEtInclusion_secombinent() {
        UmlEntity entity = EntityExtractor.extract(Panier.class);

        assertTrue(champ(entity, "total").orElseThrow().derived());
    }
}
