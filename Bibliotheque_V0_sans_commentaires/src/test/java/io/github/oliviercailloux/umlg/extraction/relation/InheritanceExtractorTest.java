package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import org.junit.jupiter.api.Test;

// L'extraction des liens d'héritage : ce que « extends » et « implements » donnent sur le
// diagramme, et ce que le périmètre @UMLClass en retire.
//
// Toutes les classes mères de ces cobayes sont annotées quand le lien doit exister, et ne le
// sont pas quand il doit disparaître : c'est exactement ce que chaque test mesure.
class InheritanceExtractorTest {

    private static ImmutableList<UmlInheritance> heritagesDe(Class<?> clazz) {
        return EntityExtractor.extract(clazz).inheritances();
    }

    @UMLClass
    static class Vehicule {}

    @UMLClass
    static class Voiture extends Vehicule {}

    @Test
    void extendsVersUneClasseDuDiagramme_donneUneGeneralisation() {
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Vehicule.class.getName())),
            heritagesDe(Voiture.class));
    }

    @UMLClass
    interface Roulant {}

    @UMLClass
    interface Immatriculable {}

    @UMLClass
    static class Camion implements Roulant {}

    @Test
    void implementsVersUneInterfaceDuDiagramme_donneUneRealisation() {
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Roulant.class.getName())),
            heritagesDe(Camion.class));
    }

    @UMLClass
    static class Remorque implements Roulant, Immatriculable {}

    @Test
    void plusieursInterfaces_donnentAutantDeRealisations() {
        assertEquals(
            ImmutableList.of(
                UmlInheritance.realization(Roulant.class.getName()),
                UmlInheritance.realization(Immatriculable.class.getName())),
            heritagesDe(Remorque.class));
    }

    @UMLClass
    static class Bus extends Vehicule implements Roulant {}

    @Test
    void extendsEtImplements_coexistentSurLaMemeClasse() {
        assertEquals(
            ImmutableList.of(
                UmlInheritance.generalization(Vehicule.class.getName()),
                UmlInheritance.realization(Roulant.class.getName())),
            heritagesDe(Bus.class));
    }

    @UMLClass
    interface Motorise extends Roulant {}

    @Test
    void interfaceEtendantUneInterface_donneUneGeneralisation() {
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Roulant.class.getName())),
            heritagesDe(Motorise.class));
    }

    @UMLClass
    sealed interface Signal permits Impulsion, Continu, NonScelle, Mixte {}

    @UMLClass
    record Impulsion(int duree) implements Signal {}

    @UMLClass
    static final class Continu implements Signal {}

    @Test
    void recordPermisParUneInterfaceScellee_donneUneGeneralisation() {
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Signal.class.getName())),
            heritagesDe(Impulsion.class));
    }

    @Test
    void classePermiseParUneInterfaceScellee_donneAussiUneGeneralisation() {
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Signal.class.getName())),
            heritagesDe(Continu.class));
    }

    @UMLClass
    non-sealed interface NonScelle extends Signal {}

    @UMLClass
    static class Bruit implements NonScelle {}

    @Test
    void interfaceNonScellee_rendLaHierarchieOuverteEtDoncRealisee() {
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(NonScelle.class.getName())),
            heritagesDe(Bruit.class));
    }

    @UMLClass
    interface Ouvert {}

    @UMLClass
    record Mixte(int valeur) implements Signal, Ouvert {}

    @Test
    void memeSource_scelleeEtOuverte_donneUnLienDeChaqueNature() {
        assertEquals(
            ImmutableList.of(
                UmlInheritance.generalization(Signal.class.getName()),
                UmlInheritance.realization(Ouvert.class.getName())),
            heritagesDe(Mixte.class));
    }

    static class OutilInterne {}

    interface ContratInterne {}

    @UMLClass
    static class HeriteHorsDiagramme extends OutilInterne implements ContratInterne {}

    @Test
    void parentHorsDiagramme_estIgnoreSansErreur() {
        assertTrue(heritagesDe(HeriteHorsDiagramme.class).isEmpty());
    }

    @UMLClass
    static class Solitaire {}

    @Test
    void objectNestJamaisUnParent() {
        assertTrue(heritagesDe(Solitaire.class).isEmpty());
    }

    @UMLClass
    enum Etat implements Roulant {
        ACTIF, INACTIF
    }

    @Test
    void enumeration_neRemonteQueSesInterfaces() {
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Roulant.class.getName())),
            heritagesDe(Etat.class));
    }

    @UMLClass
    record Trajet(int distance) implements Roulant {}

    @Test
    void record_neRemonteQueSesInterfaces() {
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Roulant.class.getName())),
            heritagesDe(Trajet.class));
    }

    @UMLClass
    static class Berline extends Voiture {}

    @Test
    void grandParent_nEstPasRelieDirectement() {
        UmlEntity entity = EntityExtractor.extract(Berline.class);

        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Voiture.class.getName())),
            entity.inheritances());
    }

    @UMLClass
    static class CamionMotorise implements Motorise {}

    @Test
    void interfaceHeritee_nEstPasRelieeDirectement() {
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Motorise.class.getName())),
            heritagesDe(CamionMotorise.class));
    }
}
