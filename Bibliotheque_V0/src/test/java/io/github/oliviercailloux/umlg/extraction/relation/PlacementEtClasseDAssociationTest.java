package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLLayout;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlLayout;
import org.junit.jupiter.api.Test;

// Ce que l'extraction tire de @UMLLayout et de @UMLAssociationClass, cobayes réels à l'appui.
//
// Les deux annotations sont éprouvées ensemble parce qu'elles se ressemblent : posées sur la
// classe, elles désignent d'autres classes, et n'ont rien à déduire du code.
//
// À noter : les classes fautives des tests de refus ne portent pas @UMLClass, et les tests
// appellent les extracteurs directement. C'est voulu — sans quoi le scan de target/test-classes,
// dans les tests d'intégration, les rencontrerait et échouerait.
class PlacementEtClasseDAssociationTest {

    @UMLClass
    static class Moteur {}

    @UMLClass
    static class Roue {}

    @UMLClass
    @UMLLayout(target = Moteur.class, direction = UMLDirection.DOWN)
    @UMLLayout(target = Roue.class)
    static class Voiture {}

    @UMLClass
    @UMLAssociationClass(source = Moteur.class, target = Roue.class)
    static class Montage {}

    // Les cobayes fautifs, que le processeur refuserait s'il tournait sur ce module : ils sont
    // ici pour éprouver la seconde ligne de défense, celle de l'extraction.
    @UMLLayout(target = Moteur.class)
    @UMLLayout(target = Moteur.class, direction = UMLDirection.UP)
    static class DeuxFoisLeMemeCote {}

    static class HorsDiagramme {}

    @UMLLayout(target = HorsDiagramme.class)
    static class VersUneBoiteInexistante {}

    @UMLAssociationClass(source = Moteur.class, target = HorsDiagramme.class)
    static class ExtremiteInexistante {}

    @UMLAssociationClass(source = SoiMeme.class, target = Moteur.class)
    static class SoiMeme {}

    @Test
    void placementsRepetes_sontTousExtraits() {
        UmlEntity voiture = EntityExtractor.extract(Voiture.class);

        assertEquals(2, voiture.layouts().size());
        assertTrue(voiture.layouts().contains(
            new UmlLayout(Moteur.class.getName(), UMLDirection.DOWN)));
    }

    @Test
    void placementSansDirection_prendNone() {
        assertTrue(EntityExtractor.extract(Voiture.class).layouts().contains(
            new UmlLayout(Roue.class.getName(), UMLDirection.NONE)));
    }

    @Test
    void memeCibleDeuxFois_estRefusee() {
        // Deux directions vers la même boîte se contrediraient, et le moteur trancherait seul.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new LayoutExtractor().extraire(DeuxFoisLeMemeCote.class));

        assertTrue(e.getMessage().contains("declared twice"), e.getMessage());
    }

    @Test
    void placementVersUneClasseHorsDiagramme_estRefuse() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new LayoutExtractor().extraire(VersUneBoiteInexistante.class));

        assertTrue(e.getMessage().contains("@UMLClass"), e.getMessage());
    }

    @Test
    void classeDAssociation_porteSesDeuxExtremites() {
        UmlEntity montage = EntityExtractor.extract(Montage.class);

        assertEquals(1, montage.associationClasses().size());
        assertEquals(new UmlAssociationClass(Moteur.class.getName(), Roue.class.getName()),
            montage.associationClasses().get(0));
    }

    @Test
    void classeSansAnnotation_nAAucuneClasseDAssociation() {
        assertTrue(EntityExtractor.extract(Moteur.class).associationClasses().isEmpty());
    }

    @Test
    void extremiteHorsDiagramme_estRefusee() {
        assertThrows(IllegalArgumentException.class,
            () -> new AssociationClassExtractor().extraire(ExtremiteInexistante.class));
    }

    @Test
    void classeDAssociationEnExtremiteDElleMeme_estRefusee() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new AssociationClassExtractor().extraire(SoiMeme.class));

        assertTrue(e.getMessage().contains("cannot be one of the two ends"), e.getMessage());
    }
}
