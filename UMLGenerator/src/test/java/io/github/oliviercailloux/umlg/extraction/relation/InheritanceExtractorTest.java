package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import org.junit.jupiter.api.Test;

// The extraction of inheritance links: what "extends" and "implements" give on the
// diagram, and what the @UMLClass perimeter takes out of it.
//
// Every parent class of these fixtures is annotated when the link must exist, and is not
// are not when it must vanish: that is exactly what each test measures.
class InheritanceExtractorTest {

    private static ImmutableList<UmlInheritance> inheritances(Class<?> clazz) {
        return EntityExtractor.extract(clazz).inheritances();
    }

    // --- Generalization: extends between two classes ---

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

    // --- Realization: implements ---

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

    // --- The UML nuance: an interface extending another generalizes, it does not realize ---

    @UMLClass
    interface Motorized extends Rolling {}

    @Test
    void interfaceExtendingAnInterface_givesAGeneralization() {
        // Java writes "extends" here and "implements" on a class, but reflection files
        // both in getInterfaces(). Only the nature of the source tells them apart.
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Rolling.class.getName())),
            inheritances(Motorized.class));
    }

    // --- Sealed hierarchy: the permitted types specialize, they do not realize ---

    @UMLClass
    sealed interface Signal permits Pulse, Continuous, NotSealed, Mixed {}

    @UMLClass
    record Pulse(int duration) implements Signal {}

    @UMLClass
    static final class Continuous implements Signal {}

    @Test
    void recordPermittedBySealedInterface_givesAGeneralization() {
        // "permits" does not enumerate candidates for a contract: it enumerates the only shapes
        // a Signal can take. The list is closed, hence it is a specialization — a
        // a realization would say the opposite, that the set of implementations is open.
        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Signal.class.getName())),
            inheritances(Pulse.class));
    }

    @Test
    void classPermittedBySealedInterface_alsoGivesAGeneralization() {
        // The rule holds by the closing of the hierarchy, not by the nature of the permitted type: an
        // an ordinary class named in "permits" is a special case on the same footing.
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
        // "non-sealed" reopens the hierarchy: anyone can now implement
        // NotSealed, and the link becomes a realization again. It is the counterpart of the previous test, and
        // it checks that the rule really follows the closing and not the mere fact of being nested
        // in a sealed hierarchy.
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
        // The same record can specialize on one side and realize on the other: the nature is decided
        // target by target, never once for the class.
        assertEquals(
            ImmutableList.of(
                UmlInheritance.generalization(Signal.class.getName()),
                UmlInheritance.realization(Open.class.getName())),
            inheritances(Mixed.class));
    }

    // --- The perimeter: an unannotated target gives no link ---

    // Deliberately without @UMLClass: this class does not belong to the diagram.
    static class InternalTool {}

    interface InternalContract {}

    @UMLClass
    static class InheritedOutsideDiagram extends InternalTool implements InternalContract {}

    @Test
    void parentOutsideTheDiagram_isIgnoredWithoutError() {
        // Unlike an association, an inheritance is not declared by the user: it
        // is read in the signature. A parent class outside the perimeter is therefore the ordinary case, not
        // a fault — hence the silence, where AssociationExtractor would raise an exception.
        assertTrue(inheritances(InheritedOutsideDiagram.class).isEmpty());
    }

    @UMLClass
    static class Lone {}

    @Test
    void objectIsNeverAParent() {
        // getSuperclass() returns Object, which carries no @UMLClass: the filter is enough, no special
        // special case is needed in the extractor.
        assertTrue(inheritances(Lone.class).isEmpty());
    }

    @UMLClass
    enum State implements Rolling {
        Active, Inactive
    }

    @Test
    void enumeration_bringsUpItsInterfacesOnly() {
        // The parent class of an enumeration is java.lang.Enum, unannotated: only the realization
        // subsiste.
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Rolling.class.getName())),
            inheritances(State.class));
    }

    @UMLClass
    record Trip(int distance) implements Rolling {}

    @Test
    void record_bringsUpItsInterfacesOnly() {
        // The same reasoning: the parent class of a record is java.lang.Record.
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Rolling.class.getName())),
            inheritances(Trip.class));
    }

    // --- Transitivity: only direct links are drawn ---

    @UMLClass
    static class Sedan extends Car {}

    @Test
    void grandParent_isNotLinkedDirectly() {
        // Car is already linked to Vehicle; redrawing Sedan -> Vehicle would duplicate
        // the information, whereas the reader follows it step by step.
        UmlEntity entity = EntityExtractor.extract(Sedan.class);

        assertEquals(
            ImmutableList.of(UmlInheritance.generalization(Car.class.getName())),
            entity.inheritances());
    }

    @UMLClass
    static class TruckMotorized implements Motorized {}

    @Test
    void inheritedInterface_isNotLinkedDirectly() {
        // Motorized extends Rolling: TruckMotorized directly realizes Motorized only.
        assertEquals(
            ImmutableList.of(UmlInheritance.realization(Motorized.class.getName())),
            inheritances(TruckMotorized.class));
    }
}
