package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

// @UMLMultiplicity : le nombre d'objets qu'une propriété tient, sur un attribut comme sur une
// méthode.
//
// Elle prend la suite de @UMLAssociation là où celle-ci ne peut rien : une collection de chaînes
// ou un entier facultatif n'ont aucune classe à l'autre bout, et ne sont donc pas des liens. Le
// dessin, lui, est le même — des crochets à côté du nom.
class PlantUmlMultipliciteTest {

    @UMLClass(name = "CanonicalMnemonic", strategy = UMLVisibility.PRIVATE)
    static class Mnemonique {
        @UMLMultiplicity("0..*")
        private String deprecatedAliases;

        @UMLMultiplicity("0..1")
        private int ucp;

        private String mnemonic;

        @UMLMultiplicity("0..*")
        public String names() {
            return "";
        }

        public String mnemonic() {
            return "";
        }
    }

    private static String source() {
        return PlantUmlAssert.documentPour(EntityExtractor.extract(Mnemonique.class));
    }

    @Test
    void surUnAttribut_lesCrochetsSuiventLeType() {
        assertTrue(source().contains("- deprecatedAliases : String [0..*]"), source());
        assertTrue(source().contains("- ucp : int [0..1]"), source());
    }

    @Test
    void surUneMethode_lesCrochetsSuiventLeTypeDeRetour() {
        // La forme des diagrammes tenus à la main : « names() : String [0..*] » se lit « rend
        // zéro, une ou plusieurs chaînes ».
        assertTrue(source().contains("+ names() : String [0..*]"), source());
        PlantUmlAssert.assertCompile(source());
    }

    @Test
    void sansAnnotation_aucunCrochetNEstEcrit() {
        assertTrue(source().contains("- mnemonic : String\n"), source());
        assertTrue(source().contains("+ mnemonic() : String\n"), source());
        assertFalse(source().contains("[]"), source());
    }

    @Test
    void lecteurVoitLesCrochets() throws Exception {
        String texte = PlantUmlAssert.texteRendu(EntityExtractor.extract(Mnemonique.class));

        assertTrue(texte.contains("deprecatedAliases : String [0..*]"), texte);
        assertTrue(texte.contains("names() : String [0..*]"), texte);
    }

    // --- Ce qui se combine avec elle ---

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Decoree {
        @UMLDerived
        @UMLConstraint(rule = "sans doublon")
        @UMLMultiplicity("1..*")
        private String etiquettes;
    }

    @Test
    void barreObliqueEtContraintes_encadrentLaMultiplicite() {
        // L'ordre d'UML : le nom, le type, la multiplicité, puis les règles.
        String source = PlantUmlAssert.documentPour(EntityExtractor.extract(Decoree.class));

        assertTrue(source.contains("- /etiquettes : String [1..*] {sans doublon}"), source);
        PlantUmlAssert.assertCompile(source);
    }

    // --- Ce qu'elle refuse ---
    //
    // Ces cobayes ne portent volontairement pas @UMLClass, et l'extraction est appelée
    // directement : le balayage de target/test-classes par les tests d'intégration les
    // extrairait sinon, et échouerait sur des fautes qui sont ici l'objet du test.

    static class MultipliciteFautive {
        @UMLMultiplicity("3..1")
        private String etiquettes;
    }

    @Test
    void multipliciteInvalide_estRefuseeEtNommeLeMembre() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(MultipliciteFautive.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("etiquettes"), e.getMessage());
        assertTrue(e.getMessage().contains("lower bound"), e.getMessage());
    }

    static class MethodeFautive {
        @UMLMultiplicity("beaucoup")
        public String noms() {
            return "";
        }
    }

    @Test
    void multipliciteInvalideSurUneMethode_estRefusee() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractMethods(MethodeFautive.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("noms"), e.getMessage());
        assertTrue(e.getMessage().contains("valid UML multiplicity"), e.getMessage());
    }

    @UMLClass
    static class Cible {}

    static class DeuxMultiplicites {
        @UMLMultiplicity("0..*")
        @UMLAssociation(target = Cible.class, targetCardinality = "1..*")
        private List<Cible> cibles;
    }

    @Test
    void avecUneAssociation_estRefusee() {
        // Deux multiplicités sur la même propriété : l'une des deux serait fausse, et rien ne
        // dirait laquelle.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MemberExtractor.extractFields(DeuxMultiplicites.class, UMLVisibility.PRIVATE));

        assertTrue(e.getMessage().contains("targetCardinality"), e.getMessage());
    }
}
