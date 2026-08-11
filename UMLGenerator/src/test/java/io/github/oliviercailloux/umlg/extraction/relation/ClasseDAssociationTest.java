package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import org.junit.jupiter.api.Test;

// Ce que l'extraction tire de @UMLAssociationClass, cobayes réels à l'appui.
//
// À noter : les classes fautives des tests de refus ne portent pas @UMLClass, et les tests
// appellent l'extracteur directement. C'est voulu — sans quoi le scan de target/test-classes,
// dans les tests d'intégration, les rencontrerait et échouerait.
class ClasseDAssociationTest {

    @UMLClass
    static class Moteur {}

    @UMLClass
    static class Roue {}

    @UMLClass
    @UMLAssociationClass(source = Moteur.class, target = Roue.class)
    static class Montage {}

    // Les cobayes fautifs, que le processeur refuserait s'il tournait sur ce module : ils sont
    // ici pour éprouver la seconde ligne de défense, celle de l'extraction.
    static class HorsDiagramme {}

    @UMLAssociationClass(source = Moteur.class, target = HorsDiagramme.class)
    static class ExtremiteInexistante {}

    @UMLAssociationClass(source = SoiMeme.class, target = Moteur.class)
    static class SoiMeme {}

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

        // L'assertion porte sur l'extrémité nommée : c'est ce que l'auteur doit corriger, et ce
        // qu'un message qui dirait seulement « une extrémité est fautive » lui laisserait chercher.
        assertTrue(e.getMessage().contains("source() is the association class itself"),
            e.getMessage());
    }
}
