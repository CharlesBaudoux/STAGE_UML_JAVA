package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Le rendu des liens d'héritage et des mots-clés de boîte.
class PlantUmlInheritanceTest {

    private static UmlEntity boite(String nomQualifie, String nom, UmlEntityKind kind) {
        return UmlEntity.builder(nomQualifie, nom).kind(kind).build();
    }

    private static UmlEntity enfant(String nom, UmlInheritance... heritages) {
        return UmlEntity.builder("pkg." + nom, nom)
            .relations(ImmutableList.copyOf(heritages))
            .build();
    }

    @Test
    void generalisation_donneUnTraitPlein() {
        UmlEntity parent = boite("pkg.Vehicule", "Vehicule", UmlEntityKind.CLASS);
        UmlEntity voiture = enfant("Voiture", UmlInheritance.generalization("pkg.Vehicule"));

        assertEquals(List.of("Vehicule <|-- Voiture"),
            PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(parent, voiture)));
    }

    @Test
    void realisation_donneUnTraitPointille() {
        UmlEntity roulant = boite("pkg.Roulant", "Roulant", UmlEntityKind.INTERFACE);
        UmlEntity camion = enfant("Camion", UmlInheritance.realization("pkg.Roulant"));

        assertEquals(List.of("Roulant <|.. Camion"),
            PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(roulant, camion)));
    }

    @Test
    void laCibleEstEcriteEnPremier() {
        UmlEntity parent = boite("pkg.Vehicule", "Vehicule", UmlEntityKind.CLASS);
        UmlEntity voiture = enfant("Voiture", UmlInheritance.generalization("pkg.Vehicule"));

        String ligne = PlantUmlAssert.lignesDeLien(
            PlantUmlAssert.documentPour(parent, voiture)).get(0);

        assertTrue(ligne.indexOf("Vehicule") < ligne.indexOf("Voiture"), ligne);
    }

    @Test
    void heritage_neRecoitAucunLabel() {
        UmlEntity parent = boite("pkg.Vehicule", "Vehicule", UmlEntityKind.CLASS);
        UmlEntity voiture = enfant("Voiture", UmlInheritance.generalization("pkg.Vehicule"));

        assertTrue(PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(parent, voiture))
                                 .get(0).indexOf(':') < 0);
    }

    @Test
    void plusieursHeritages_donnentAutantDeLignes() {
        UmlEntity roulant = boite("pkg.Roulant", "Roulant", UmlEntityKind.INTERFACE);
        UmlEntity vehicule = boite("pkg.Vehicule", "Vehicule", UmlEntityKind.ABSTRACT_CLASS);
        UmlEntity bus = enfant("Bus",
            UmlInheritance.generalization("pkg.Vehicule"),
            UmlInheritance.realization("pkg.Roulant"));

        assertEquals(List.of("Vehicule <|-- Bus", "Roulant <|.. Bus"),
            PlantUmlAssert.lignesDeLien(PlantUmlAssert.documentPour(roulant, vehicule, bus)));
    }

    @ParameterizedTest
    @CsvSource({
        "CLASS,           class",
        "ABSTRACT_CLASS,  abstract class",
        "INTERFACE,       interface",
        "ENUM,            enum",
        "ANNOTATION,      annotation"
    })
    void natureDeLEntite_choisitLeMotCle(UmlEntityKind kind, String motCle) {
        String source = PlantUmlAssert.documentPour(boite("pkg.E", "E", kind));

        assertTrue(source.contains(motCle + " \"E\" as E {"), source);
    }

    @Test
    void toutesLesNatures_sontAccepteesParPlantUml() {
        UmlEntity[] boites = new UmlEntity[UmlEntityKind.values().length];
        int i = 0;
        for (UmlEntityKind kind : UmlEntityKind.values()) {
            boites[i] = boite("pkg." + kind.name(), kind.name(), kind);
            i++;
        }

        PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(boites));
    }

    @Test
    void hierarchieComplete_estAccepteeParPlantUml() {
        UmlEntity roulant = boite("pkg.Roulant", "Roulant", UmlEntityKind.INTERFACE);
        UmlEntity vehicule = boite("pkg.Vehicule", "Vehicule", UmlEntityKind.ABSTRACT_CLASS);
        UmlEntity bus = enfant("Bus",
            UmlInheritance.generalization("pkg.Vehicule"),
            UmlInheritance.realization("pkg.Roulant"));

        PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(roulant, vehicule, bus));
    }

    @Test
    void cibleNonDeclaree_estSignaleeCommeUnHeritage() {
        UmlEntity orphelin = enfant("Orphelin", UmlInheritance.generalization("pkg.Absente"));

        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class, () -> PlantUmlAssert.documentPour(orphelin));

        assertTrue(e.getMessage().contains("generalization"), e.getMessage());
        assertTrue(e.getMessage().contains("pkg.Absente"), e.getMessage());
    }
}
