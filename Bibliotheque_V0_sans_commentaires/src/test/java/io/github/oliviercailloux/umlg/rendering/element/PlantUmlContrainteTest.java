package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// Le rendu des contraintes : le texte entre accolades d'UML.
//
// Trois positions, une seule notation. Ce que ces tests vérifient est que les accolades sont
// posées par le générateur — l'utilisateur écrit la règle nue — et qu'elles atterrissent au bon
// endroit selon ce que la contrainte vise.
class PlantUmlContrainteTest {

    private static UmlEntity boite(List<UmlField> champs, List<String> contraintes) {
        return UmlEntity.builder("com.exemple.Compte", "Compte")
            .fields(champs).constraints(contraintes).build();
    }

    @Test
    void contrainteDAttribut_suitLaLigneDeLAttribut() {
        String source = PlantUmlAssert.documentPour(boite(
            List.of(new UmlField("solde", "double", "-", false, false,
                ImmutableList.of("toujours positif"))),
            List.of()));

        assertTrue(source.contains("- double solde {toujours positif}"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void regleContenantDuBalisage_estEchappee() {
        String source = PlantUmlAssert.documentPour(boite(
            List.of(new UmlField("solde", "double", "-", false, false,
                ImmutableList.of("solde >= 0"))),
            List.of()));

        assertTrue(source.contains("{solde &#62;= 0}"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void contraintesMultiples_seSuiventSurLaMemeLigne() {
        String source = PlantUmlAssert.documentPour(boite(
            List.of(new UmlField("solde", "double", "-", false, false,
                ImmutableList.of("positif", "arrondi au centime"))),
            List.of()));

        assertTrue(source.contains("- double solde {positif} {arrondi au centime}"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void contrainteEtDerivation_cohabitent() {
        String source = PlantUmlAssert.documentPour(boite(
            List.of(new UmlField("age", "int", "-", false, true,
                ImmutableList.of("toujours positif"))),
            List.of()));

        assertTrue(source.contains("- int /age {toujours positif}"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void contrainteDeClasse_estDessineeCommeUneBulle() {
        String source = PlantUmlAssert.documentPour(boite(List.of(),
            List.of("titulaire majeur")));

        assertEquals(List.of("note right of Compte : {titulaire majeur}"),
            source.lines().filter(l -> l.startsWith("note ")).toList());
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void contrainteDeLien_rejointLExtremiteCible() {
        UmlAssociation association = UmlAssociation.builder("commandes", "com.exemple.Commande")
            .targetRole("achats").targetCardinality("0..*")
            .constraints(List.of("subset")).build();

        String ligne = ligneDeLien(association);

        assertTrue(ligne.contains("\"achats 0..* {subset}\""), ligne);
    }

    @Test
    void contrainteDeLien_coexisteAvecOrdered() {
        UmlAssociation association = UmlAssociation.builder("commandes", "com.exemple.Commande")
            .targetCardinality("0..*").ordered(true)
            .constraints(List.of("subset")).build();

        assertTrue(ligneDeLien(association).contains("\"0..* {ordered} {subset}\""),
            ligneDeLien(association));
    }

    @Test
    void lecteurVoitLaContrainte() throws Exception {
        String texte = PlantUmlAssert.texteRendu(boite(
            List.of(new UmlField("solde", "double", "-", false, false,
                ImmutableList.of("solde >= 0"))),
            List.of()));

        assertTrue(texte.contains("{solde >= 0}"), texte);
    }

    private static String ligneDeLien(UmlAssociation association) {
        UmlEntity source = UmlEntity.builder("com.exemple.Client", "Client")
            .associations(ImmutableList.of(association)).build();
        UmlEntity cible = UmlEntity.builder("com.exemple.Commande", "Commande").build();
        String document = PlantUmlAssert.documentPour(source, cible);

        List<String> lignes = PlantUmlAssert.lignesDeLien(document);
        assertEquals(1, lignes.size(), document);
        PlantUmlAssert.assertCompile(document);
        return lignes.get(0);
    }
}
