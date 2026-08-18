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

class AssociationClassTest {

    @UMLClass
    static class Engine {}

    @UMLClass
    static class Wheel {}

    @UMLClass
    @UMLAssociationClass(source = Engine.class, target = Wheel.class)
    static class Assembly {}

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

        assertTrue(e.getMessage().contains("source() is the association class itself"),
            e.getMessage());
    }
}
