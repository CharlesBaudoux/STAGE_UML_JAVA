package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// Le rendu du compartiment des littéraux, et le placement des filets séparateurs quand une boîte
// compte jusqu'à trois compartiments.
class PlantUmlEnumTest {

    private static UmlEntity.Builder enumeration(String nom) {
        return UmlEntity.builder("pkg." + nom, nom)
                        .kind(UmlEntityKind.ENUM)
                        .strategy(UMLVisibility.PUBLIC);
    }

    @Test
    void litteral_estEcritNu() {
        String source = PlantUmlAssert.documentPour(
            enumeration("Devise").literals(List.of("EURO", "DOLLAR")).build());

        assertTrue(source.contains("  EURO\n"), source);
        assertTrue(source.contains("  DOLLAR\n"), source);
    }

    @Test
    void litteral_nAJamaisNiVisibiliteNiTypeNiStatic() {
        String source = PlantUmlAssert.documentPour(
            enumeration("Devise").literals(List.of("EURO")).build());

        assertFalse(source.contains("+ EURO"), source);
        assertFalse(source.contains("{static}"), source);
        assertFalse(source.contains("Devise EURO"), source);
    }

    @Test
    void litterauxSeuls_nOntAucunFiletSeparateur() {
        String source = PlantUmlAssert.documentPour(
            enumeration("Devise").literals(List.of("EURO", "DOLLAR")).build());

        assertFalse(source.contains("  --\n"), source);
    }

    @Test
    void litterauxEtAttributs_sontSeparesParUnFilet() {
        String source = PlantUmlAssert.documentPour(
            enumeration("Planete")
                .literals(List.of("TERRE"))
                .fields(List.of(new UmlField("masse", "double", "-", false)))
                .build());

        assertEquals(List.of("  TERRE", "  --", "  - double masse"),
            corpsDeLaBoite(source));
    }

    @Test
    void troisCompartiments_recoiventDeuxFilets() {
        String source = PlantUmlAssert.documentPour(
            enumeration("Planete")
                .literals(List.of("TERRE"))
                .fields(List.of(new UmlField("masse", "double", "-", false)))
                .methods(List.of(new UmlMethod("masse", "double", "+", false, List.of())))
                .build());

        assertEquals(
            List.of("  TERRE", "  --", "  - double masse", "  --", "  + double masse()"),
            corpsDeLaBoite(source));
    }

    @Test
    void classeOrdinaire_gardeSonUniqueFilet() {
        String source = PlantUmlAssert.documentPour(
            UmlEntity.builder("pkg.Compte", "Compte")
                     .fields(List.of(new UmlField("solde", "double", "-", false)))
                     .methods(List.of(new UmlMethod("retirer", "void", "+", false,
                         List.of("double"))))
                     .build());

        assertEquals(List.of("  - double solde", "  --", "  + void retirer(double)"),
            corpsDeLaBoite(source));
    }

    @Test
    void enumerationRendue_estAccepteeParPlantUml() {
        PlantUmlAssert.assertCompile(PlantUmlAssert.documentPour(
            enumeration("Devise")
                .literals(List.of("EURO", "DOLLAR"))
                .fields(List.of(new UmlField("code", "String", "-", false)))
                .build()));
    }

    private static List<String> corpsDeLaBoite(String source) {
        return source.lines().filter(l -> l.startsWith("  ")).toList();
    }
}
