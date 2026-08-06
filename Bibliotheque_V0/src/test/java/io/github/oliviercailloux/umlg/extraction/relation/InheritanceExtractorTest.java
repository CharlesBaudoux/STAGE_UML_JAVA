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

    // --- Généralisation : extends entre deux classes ---

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

    // --- Réalisation : implements ---

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

    // --- La nuance UML : une interface qui en étend une autre généralise, elle ne réalise pas ---

    @UMLClass
    interface Motorise extends Roulant {}

    @Test
    void interfaceEtendantUneInterface_donneUneGeneralisation() {
        // Java écrit « extends » ici et « implements » sur une classe, mais la réflexion range
        // les deux dans getInterfaces(). Seule la nature de la source les distingue.
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Roulant.class.getName())),
            heritagesDe(Motorise.class));
    }

    // --- Hiérarchie scellée : les types permis spécialisent, ils ne réalisent pas ---

    @UMLClass
    sealed interface Signal permits Impulsion, Continu, NonScelle, Mixte {}

    @UMLClass
    record Impulsion(int duree) implements Signal {}

    @UMLClass
    static final class Continu implements Signal {}

    @Test
    void recordPermisParUneInterfaceScellee_donneUneGeneralisation() {
        // « permits » n'énumère pas des candidats à un contrat : il énumère les seules formes
        // qu'un Signal puisse prendre. La liste est fermée, donc c'est une spécialisation — une
        // réalisation dirait l'inverse, que l'ensemble des implémentations est ouvert.
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Signal.class.getName())),
            heritagesDe(Impulsion.class));
    }

    @Test
    void classePermiseParUneInterfaceScellee_donneAussiUneGeneralisation() {
        // La règle tient à la fermeture de la hiérarchie, pas à la nature du type permis : une
        // classe ordinaire citée dans « permits » est un cas particulier au même titre.
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
        // « non-sealed » rouvre la hiérarchie : n'importe qui peut désormais implémenter
        // NonScelle, et le lien redevient une réalisation. C'est le pendant du test précédent, et
        // il vérifie que la règle suit bien la fermeture et non le simple fait d'être imbriquée
        // dans une hiérarchie scellée.
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
        // Le même record peut spécialiser d'un côté et réaliser de l'autre : la nature se décide
        // cible par cible, jamais une fois pour la classe.
        assertEquals(
            ImmutableList.of(
                UmlInheritance.generalization(Signal.class.getName()),
                UmlInheritance.realization(Ouvert.class.getName())),
            heritagesDe(Mixte.class));
    }

    // --- Le périmètre : une cible non annotée ne donne aucun lien ---

    // Volontairement sans @UMLClass : cette classe n'appartient pas au diagramme.
    static class OutilInterne {}

    interface ContratInterne {}

    @UMLClass
    static class HeriteHorsDiagramme extends OutilInterne implements ContratInterne {}

    @Test
    void parentHorsDiagramme_estIgnoreSansErreur() {
        // Contrairement à une association, un héritage n'est pas déclaré par l'utilisateur : il
        // est lu dans la signature. Une classe mère hors périmètre est donc le cas courant, pas
        // une faute — d'où le silence, là où AssociationExtractor lèverait une exception.
        assertTrue(heritagesDe(HeriteHorsDiagramme.class).isEmpty());
    }

    @UMLClass
    static class Solitaire {}

    @Test
    void objectNestJamaisUnParent() {
        // getSuperclass() rend Object, qui ne porte pas @UMLClass : le filtre suffit, aucun cas
        // particulier n'est nécessaire dans l'extracteur.
        assertTrue(heritagesDe(Solitaire.class).isEmpty());
    }

    @UMLClass
    enum Etat implements Roulant {
        ACTIF, INACTIF
    }

    @Test
    void enumeration_neRemonteQueSesInterfaces() {
        // La classe mère d'une énumération est java.lang.Enum, non annotée : seule la réalisation
        // subsiste.
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Roulant.class.getName())),
            heritagesDe(Etat.class));
    }

    @UMLClass
    record Trajet(int distance) implements Roulant {}

    @Test
    void record_neRemonteQueSesInterfaces() {
        // Même raisonnement : la classe mère d'un record est java.lang.Record.
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Roulant.class.getName())),
            heritagesDe(Trajet.class));
    }

    // --- Transitivité : seuls les liens directs sont dessinés ---

    @UMLClass
    static class Berline extends Voiture {}

    @Test
    void grandParent_nEstPasRelieDirectement() {
        // Voiture est déjà reliée à Vehicule ; redessiner Berline -> Vehicule doublerait
        // l'information, alors que le lecteur la suit de proche en proche.
        UmlEntity entity = EntityExtractor.extract(Berline.class);

        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Voiture.class.getName())),
            entity.inheritances());
    }

    @UMLClass
    static class CamionMotorise implements Motorise {}

    @Test
    void interfaceHeritee_nEstPasRelieeDirectement() {
        // Motorise étend Roulant : CamionMotorise ne réalise directement que Motorise.
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Motorise.class.getName())),
            heritagesDe(CamionMotorise.class));
    }
}
