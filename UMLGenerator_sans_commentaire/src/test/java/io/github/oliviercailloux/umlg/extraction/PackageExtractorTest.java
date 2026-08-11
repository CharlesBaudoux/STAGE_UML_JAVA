package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import org.junit.jupiter.api.Test;

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
        assertTrue(groupe.estGroupee());
    }

    @Test
    void classeSansAnnotation_nEstDansAucunGroupe() {
        UmlPackage groupe = EntityExtractor.extract(HorsGroupe.class).group();

        assertEquals(UmlPackage.aucun(), groupe);
        assertFalse(groupe.estGroupee());
    }

    @Test
    void nomBlanc_estRefuse() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PackageExtractor.extraire(NomBlanc.class));

        assertTrue(e.getMessage().contains("name()"), e.getMessage());
    }

    @Test
    void couleurMalFormee_estRefusee() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PackageExtractor.extraire(CouleurMalFormee.class));

        assertTrue(e.getMessage().contains("@UMLPackage"), e.getMessage());
        assertTrue(e.getMessage().contains("Color unknown to PlantUML"), e.getMessage());
    }

    @Test
    void groupeSansNomMaisAvecCouleur_estRefuseParLeModele() {
        assertThrows(IllegalArgumentException.class, () -> new UmlPackage("", "#EEEEEE"));
    }
}
