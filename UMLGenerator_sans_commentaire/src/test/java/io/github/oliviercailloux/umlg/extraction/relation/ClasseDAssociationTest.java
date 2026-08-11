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

class ClasseDAssociationTest {

    @UMLClass
    static class Moteur {}

    @UMLClass
    static class Roue {}

    @UMLClass
    @UMLAssociationClass(source = Moteur.class, target = Roue.class)
    static class Montage {}

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

        assertTrue(e.getMessage().contains("cannot be one of the two ends"), e.getMessage());
    }
}
