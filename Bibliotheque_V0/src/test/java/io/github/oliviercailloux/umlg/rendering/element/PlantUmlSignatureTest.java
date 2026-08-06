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

// La notation « nom: Type », aux trois endroits où un type paraît : un attribut, le retour d'une
// méthode, un paramètre.
//
// Sur les noms de paramètres. Ce projet-ci est compilé avec l'option « -parameters » (voir le
// pom), les cobayes qui suivent portent donc leurs noms et les tests peuvent les attendre. Un
// projet compilé sans elle n'en a pas, et la bibliothèque n'écrit alors que les types — c'est le
// cas qu'éprouve ProjectScannerIntegrationTest, qui compile ses cobayes sans l'option.
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
        assertTrue(source().contains("- departureTime: String"), source());
    }

    @Test
    void methode_ecritSonTypeDeRetourApresLesParentheses() {
        assertTrue(source().contains("+ destination(): String"), source());
    }

    @Test
    void parametres_portentLeurNomQuandLaCompilationLaGarde() {
        assertTrue(source().contains("+ reporter(nouvelleHeure: String, minutes: int): void"),
            "Les paramètres doivent être nommés et séparés par une virgule :\n" + source());
        // Le nom inventé par la réflexion ne doit jamais atteindre le dessin.
        assertFalse(source().contains("arg0"), source());
    }

    @Test
    void staticEtDerive_gardentLeursNotationsAutourDeLaSignature() {
        // {static} avant le nom, la barre oblique devant lui, le type de retour après : les trois
        // notations doivent coexister sans se marcher dessus.
        assertTrue(source().contains("+ {static} /compter(code: String): int"), source());
        PlantUmlAssert.assertCompile(source());
    }

    @Test
    void parametreSansNom_neMontreQueSonType() {
        // Le modèle construit à la main, comme le ferait l'extraction d'un projet compilé sans
        // « -parameters » : sans nom, pas de deux-points orphelins.
        UmlEntity entite = UmlEntity.builder("com.exemple.Compte", "Compte")
            .methods(List.of(new UmlMethod("retirer", "void", "", "+", false,
                ImmutableList.of(UmlParameter.anonyme("double"), new UmlParameter("motif",
                    "String")),
                false)))
            .build();

        String source = PlantUmlAssert.documentPour(entite);

        assertTrue(source.contains("+ retirer(double, motif: String): void"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void lecteurVoitLaSignatureComplete() throws Exception {
        String texte = PlantUmlAssert.texteRendu(EntityExtractor.extract(Vol.class));

        assertTrue(texte.contains("departureTime: String"), texte);
        assertTrue(texte.contains("reporter(nouvelleHeure: String, minutes: int): void"), texte);
    }
}
