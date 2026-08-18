package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import org.junit.jupiter.api.Test;

class InheritanceExtractorTest {

    private static ImmutableList<UmlInheritance> inheritances(Class<?> clazz) {
        return EntityExtractor.extract(clazz).inheritances();
    }

    @UMLClass
    static class Vehicle {}

    @UMLClass
    static class Car extends Vehicle {}

    @Test
    void extendsTowardsADiagramClass_givesAGeneralization() {
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Vehicle.class.getName())),
            inheritances(Car.class));
    }

    @UMLClass
    interface Rolling {}

    @UMLClass
    interface Registrable {}

    @UMLClass
    static class Truck implements Rolling {}

    @Test
    void implementsTowardsADiagramInterface_givesARealization() {
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Rolling.class.getName())),
            inheritances(Truck.class));
    }

    @UMLClass
    static class Trailer implements Rolling, Registrable {}

    @Test
    void severalInterfaces_giveAsManyRealizations() {
        assertEquals(
            ImmutableList.of(
                UmlInheritance.realization(Rolling.class.getName()),
                UmlInheritance.realization(Registrable.class.getName())),
            inheritances(Trailer.class));
    }

    @UMLClass
    static class Bus extends Vehicle implements Rolling {}

    @Test
    void extendsAndImplements_coexistOnTheSameClass() {
        assertEquals(
            ImmutableList.of(
                UmlInheritance.generalization(Vehicle.class.getName()),
                UmlInheritance.realization(Rolling.class.getName())),
            inheritances(Bus.class));
    }

    @UMLClass
    interface Motorized extends Rolling {}

    @Test
    void interfaceExtendingAnInterface_givesAGeneralization() {
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Rolling.class.getName())),
            inheritances(Motorized.class));
    }

    @UMLClass
    sealed interface Signal permits Pulse, Continuous, NotSealed, Mixed {}

    @UMLClass
    record Pulse(int duration) implements Signal {}

    @UMLClass
    static final class Continuous implements Signal {}

    @Test
    void recordPermittedBySealedInterface_givesAGeneralization() {
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Signal.class.getName())),
            inheritances(Pulse.class));
    }

    @Test
    void classPermittedBySealedInterface_alsoGivesAGeneralization() {
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Signal.class.getName())),
            inheritances(Continuous.class));
    }

    @UMLClass
    non-sealed interface NotSealed extends Signal {}

    @UMLClass
    static class Noise implements NotSealed {}

    @Test
    void unsealedInterface_makesTheHierarchyOpenAndThusRealized() {
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(NotSealed.class.getName())),
            inheritances(Noise.class));
    }

    @UMLClass
    interface Open {}

    @UMLClass
    record Mixed(int value) implements Signal, Open {}

    @Test
    void sameSource_sealedAndOpen_givesOneLinkOfEachNature() {
        assertEquals(
            ImmutableList.of(
                UmlInheritance.generalization(Signal.class.getName()),
                UmlInheritance.realization(Open.class.getName())),
            inheritances(Mixed.class));
    }

    static class InternalTool {}

    interface InternalContract {}

    @UMLClass
    static class InheritedOutsideDiagram extends InternalTool implements InternalContract {}

    @Test
    void parentOutsideTheDiagram_isIgnoredWithoutError() {
        assertTrue(inheritances(InheritedOutsideDiagram.class).isEmpty());
    }

    @UMLClass
    static class Lone {}

    @Test
    void objectIsNeverAParent() {
        assertTrue(inheritances(Lone.class).isEmpty());
    }

    @UMLClass
    enum State implements Rolling {
        Active, Inactive
    }

    @Test
    void enumeration_bringsUpItsInterfacesOnly() {
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Rolling.class.getName())),
            inheritances(State.class));
    }

    @UMLClass
    record Trip(int distance) implements Rolling {}

    @Test
    void record_bringsUpItsInterfacesOnly() {
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Rolling.class.getName())),
            inheritances(Trip.class));
    }

    @UMLClass
    static class Sedan extends Car {}

    @Test
    void grandParent_isNotLinkedDirectly() {
        UmlEntity entity = EntityExtractor.extract(Sedan.class);

        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Car.class.getName())),
            entity.inheritances());
    }

    @UMLClass
    static class TruckMotorized implements Motorized {}

    @Test
    void inheritedInterface_isNotLinkedDirectly() {
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Motorized.class.getName())),
            inheritances(TruckMotorized.class));
    }
}
