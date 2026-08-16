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

// What extraction draws from @UMLAssociationClass, backed by real fixtures.
//
// Note: the faulty classes of the refusal tests carry no @UMLClass, and the tests
// call the extractor directly. That is intended — otherwise the scan of target/test-classes,
// in the integration tests, would meet them and fail.
class AssociationClassTest {

    @UMLClass
    static class Engine {}

    @UMLClass
    static class Wheel {}

    @UMLClass
    @UMLAssociationClass(source = Engine.class, target = Wheel.class)
    static class Assembly {}

    // The faulty fixtures, which the processor would refuse if it ran on this module: they are
    // here to exercise the second line of defence, that of extraction.
    static class OutsideDiagram {}

    @UMLAssociationClass(source = Engine.class, target = OutsideDiagram.class)
    static class MissingEndpoint {}

    @UMLAssociationClass(source = Self.class, target = Engine.class)
    static class Self {}

    @Test
    void associationClass_carriesBothItsEndpoints() {
        UmlEntity assembly = EntityExtractor.extract(Assembly.class);

        assertEquals(1, assembly.associationClasses().size());
        assertEquals(new UmlAssociationClass(Engine.class.getName(), Wheel.class.getName()),
            assembly.associationClasses().get(0));
    }

    @Test
    void classWithoutAnnotation_hasNoAssociationClass() {
        assertTrue(EntityExtractor.extract(Engine.class).associationClasses().isEmpty());
    }

    @Test
    void endpointOutsideTheDiagram_isRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new AssociationClassExtractor().extract(MissingEndpoint.class));
    }

    @Test
    void associationClassAsItsOwnEndpoint_isRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new AssociationClassExtractor().extract(Self.class));

        // The assertion bears on the named endpoint: that is what the author must fix, and what
        // than a message merely saying "an endpoint is faulty" would leave them looking for.
        assertTrue(e.getMessage().contains("source() is the association class itself"),
            e.getMessage());
    }
}
