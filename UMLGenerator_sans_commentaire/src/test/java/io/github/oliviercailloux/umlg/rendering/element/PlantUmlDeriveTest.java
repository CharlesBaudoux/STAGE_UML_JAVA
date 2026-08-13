package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantUmlDeriveTest {

    private static UmlEntity boite(List<UmlField> champs, List<UmlMethod> methodes) {
        return UmlEntity.builder("com.exemple.Personne", "Personne")
            .fields(champs).methods(methodes).build();
    }

    @Test
    void attributDerive_prendLaBarreObliqueDevantSonNom() {
        String source = PlantUmlAssert.documentPour(
            boite(List.of(new UmlField("age", "int", "", "-", false, true, ImmutableList.of())),
                List.of()));

        assertTrue(source.contains("- /age : int"),
            "La barre doit précéder le nom, et rien d'autre :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void attributOrdinaire_nEnPrendPas() {
        String source = PlantUmlAssert.documentPour(
            boite(List.of(new UmlField("solde", "double", "-", false)), List.of()));

        assertTrue(source.contains("- solde : double"), source);
        assertFalse(source.contains("/solde"), source);
    }

    @Test
    void attributDeriveEtStatique_gardeLesDeuxNotations() {
        String source = PlantUmlAssert.documentPour(
            boite(List.of(new UmlField("total", "double", "", "+", true, true, ImmutableList.of())),
                List.of()));

        assertTrue(source.contains("+ {static} /total : double"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void methodeDerivee_prendLaBarreDevantSonNom() {
        String source = PlantUmlAssert.documentPour(boite(List.of(),
            List.of(new UmlMethod("age", "int", "", "+", false, ImmutableList.of(), true))));

        assertTrue(source.contains("+ /age() : int"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void lienDerive_marqueLeRoleCible() {
        UmlAssociation association = UmlAssociation.builder("commandes", "com.exemple.Commande")
            .targetRole("encours").targetMultiplicity("0..*").derived(true).build();

        String source = source(association);

        assertEquals(1, PlantUmlAssert.lignesDeLien(source).size(), source);
        assertTrue(PlantUmlAssert.lignesDeLien(source).get(0).contains("\"/encours 0..*\""),
            "La barre précède le rôle, pas la multiplicité :\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void lienDeriveSansRole_neMarqueRien() {
        UmlAssociation association = UmlAssociation.builder("commandes", "com.exemple.Commande")
            .targetMultiplicity("0..*").derived(true).build();

        String source = source(association);
        String lien = PlantUmlAssert.lignesDeLien(source).get(0);

        assertFalse(lien.contains("/"), "Aucune barre ne doit apparaître sur le trait : " + lien);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void lecteurVoitLaBarre() throws Exception {
        String texte = PlantUmlAssert.texteRendu(
            boite(List.of(new UmlField("age", "int", "", "-", false, true, ImmutableList.of())),
                List.of()));

        assertTrue(texte.contains("/age"), texte);
    }

    private static String source(UmlAssociation association) {
        UmlEntity source = UmlEntity.builder("com.exemple.Client", "Client")
            .associations(ImmutableList.of(association)).build();
        UmlEntity cible = UmlEntity.builder("com.exemple.Commande", "Commande").build();
        return PlantUmlAssert.documentPour(source, cible);
    }
}
