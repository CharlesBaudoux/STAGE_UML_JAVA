package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import java.util.List;
import org.junit.jupiter.api.Test;

// « La classe à l'autre bout du trait a-t-elle le droit d'y être ? »
//
// target() étant obligatoire (sans default, javac refuse déjà une annotation où elle manque),
// il n'y a plus rien à déduire : ces tests vérifient la seule validation — la cible déclarée
// est acceptée telle quelle, ou refusée avec un message qui nomme la faute.
class TargetResolverTest {

    @UMLClass
    static class Commande {}

    static class HorsDiagramme {}

    private static UmlAssociation seuleAssociation(Class<?> clazz) {
        List<UmlAssociation> associations = EntityExtractor.extract(clazz).associations();
        assertEquals(1, associations.size(),
            () -> "Attendu exactement une association sur " + clazz.getSimpleName()
                + ", obtenu : " + associations);
        return associations.get(0);
    }

    @UMLClass
    static class ChampSimple {
        @UMLAssociation(target = Commande.class)
        private Commande commande;
    }

    @Test
    void cibleDeclaree_estRepriseTelleQuelle() {
        assertEquals(Commande.class.getName(),
            seuleAssociation(ChampSimple.class).targetQualifiedName());
    }

    @UMLClass
    static class ChampCibleIndependanteDuType {
        @UMLAssociation(target = Commande.class)
        private List<Object> boiteNoire;
    }

    @Test
    void cibleDeclaree_nEstJamaisConfronteeAuTypeDeLAttribut() {
        assertEquals(Commande.class.getName(),
            seuleAssociation(ChampCibleIndependanteDuType.class).targetQualifiedName());
    }

    static class ChampHorsDiagramme {
        @UMLAssociation(target = HorsDiagramme.class)
        private HorsDiagramme dependance;
    }

    @Test
    void cibleSansUmlClass_estRefusee() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new AssociationExtractor().extraire(ChampHorsDiagramme.class));

        assertTrue(e.getMessage().contains("@UMLClass"),
            () -> "Le message doit expliquer ce qui manque : " + e.getMessage());
        assertTrue(e.getMessage().contains("dependance"),
            () -> "Le message doit désigner l'attribut fautif : " + e.getMessage());
    }

    static class ChampPrimitif {
        @UMLAssociation(target = int.class)
        private int compteur;
    }

    @Test
    void typePrimitif_estRefuse() {
        assertThrows(IllegalArgumentException.class,
            () -> new AssociationExtractor().extraire(ChampPrimitif.class));
    }
}
