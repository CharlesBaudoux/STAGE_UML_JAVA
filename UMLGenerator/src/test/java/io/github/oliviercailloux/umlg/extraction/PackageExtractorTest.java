package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import org.junit.jupiter.api.Test;

// Ce que l'extraction tire de @UMLPackage.
//
// À noter : les classes fautives des tests de refus ne portent pas @UMLClass, et les tests
// appellent PackageExtractor directement. C'est voulu — sans quoi le scan de target/test-classes,
// dans les tests d'intégration, les rencontrerait et échouerait.
class PackageExtractorTest {

    @UMLClass
    @UMLPackage(name = "Module Paiement", color = "#EEEEEE")
    static class Facture {}

    @UMLClass
    @UMLPackage(name = "Module Paiement")
    static class Ligne {}

    @UMLClass
    static class HorsGroupe {}

    @UMLPackage(name = "   ")
    static class NomBlanc {}

    // Un hexadécimal impossible : ce que refuse le contrôle de forme, seul à s'appliquer ici.
    // Un nom de couleur inconnu du moteur — « PasUneCouleur » — a la forme d'une couleur et ne
    // se voit refuser qu'au rendu, par ColorCodec.
    @UMLPackage(name = "Groupe", color = "#GGGGGG")
    static class CouleurMalFormee {}

    @Test
    void groupeDeclare_estLu() {
        assertEquals(new UmlPackage("Module Paiement", "#EEEEEE"),
            EntityExtractor.extract(Facture.class).group());
    }

    @Test
    void groupeSansCouleur_aUneCouleurVide() {
        UmlPackage groupe = EntityExtractor.extract(Ligne.class).group();

        assertEquals("Module Paiement", groupe.name());
        assertEquals("", groupe.color());
        assertTrue(groupe.isGrouped());
    }

    @Test
    void classeSansAnnotation_nEstDansAucunGroupe() {
        UmlPackage groupe = EntityExtractor.extract(HorsGroupe.class).group();

        assertEquals(UmlPackage.none(), groupe);
        assertFalse(groupe.isGrouped());
    }

    @Test
    void nomBlanc_estRefuse() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PackageExtractor.extraire(NomBlanc.class));

        assertTrue(e.getMessage().contains("name()"), e.getMessage());
    }

    @Test
    void couleurMalFormee_estRefusee() {
        // La même grammaire que pour une boîte, et le message nomme l'annotation réellement
        // écrite — @UMLPackage, et non @UMLClass.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PackageExtractor.extraire(CouleurMalFormee.class));

        assertTrue(e.getMessage().contains("@UMLPackage"), e.getMessage());
        // La grammaire est celle du catalogue de PlantUML, la même qu'au dessin et à la
        // compilation : une seule question posée à un seul juge.
        assertTrue(e.getMessage().contains("Color unknown to PlantUML"), e.getMessage());
    }

    @Test
    void groupeSansNomMaisAvecCouleur_estRefuseParLeModele() {
        // Le cas ne peut pas venir de l'annotation — name() y est obligatoire — mais le modèle
        // se garde lui-même : une couleur sans groupe ne serait posée sur rien.
        assertThrows(IllegalArgumentException.class, () -> new UmlPackage("", "#EEEEEE"));
    }
}
