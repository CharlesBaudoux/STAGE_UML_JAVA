package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantUmlSignatureTest {

    @UMLClass(name = "Vol", strategy = UMLVisibility.PRIVATE)
    static class Vol {
        private String departureTime;

        public void reporter(String nouvelleHeure, int minutes) {}

        public String destination() {
            return "";
        }

        @UMLDerived
        public static int compter(String code) {
            return 0;
        }
    }

    private static String source() {
        return PlantUmlAssert.documentPour(EntityExtractor.extract(Vol.class));
    }

    @Test
    void attribut_secritNomPuisType() {
        assertTrue(source().contains("- departureTime : String"), source());
    }

    @Test
    void methode_ecritSonTypeDeRetourApresLesParentheses() {
        assertTrue(source().contains("+ destination() : String"), source());
    }

    @Test
    void parametres_portentLeurNomQuandLaCompilationLaGarde() {
        assertTrue(source().contains("+ reporter(nouvelleHeure : String, minutes : int) : void"),
            "Les paramètres doivent être nommés et séparés par une virgule :\n" + source());
        assertFalse(source().contains("arg0"), source());
    }

    @Test
    void staticEtDerive_gardentLeursNotationsAutourDeLaSignature() {
        assertTrue(source().contains("+ {static} /compter(code : String) : int"), source());
        PlantUmlAssert.assertCompile(source());
    }

    @Test
    void parametreSansNom_neMontreQueSonType() {
        UmlEntity entite = UmlEntity.builder("com.exemple.Compte", "Compte")
            .methods(List.of(new UmlMethod("retirer", "void", "", "+", false,
                ImmutableList.of(UmlParameter.anonyme("double"), new UmlParameter("motif",
                    "String")),
                false)))
            .build();

        String source = PlantUmlAssert.documentPour(entite);

        assertTrue(source.contains("+ retirer(double, motif : String) : void"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void lecteurVoitLaSignatureComplete() throws Exception {
        String texte = PlantUmlAssert.texteRendu(EntityExtractor.extract(Vol.class));

        assertTrue(texte.contains("departureTime : String"), texte);
        assertTrue(texte.contains("reporter(nouvelleHeure : String, minutes : int) : void"), texte);
    }
}
