package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import java.util.List;
import org.junit.jupiter.api.Test;

// The orchestrator's own contract: read the @UMLClass header, and call every
// relation extractors.
//
// The demonstration classes are declared right here, as static nested ones: they are annotated
// as a user would, and extraction working by reflection, that is enough. No
// separate sample file is needed, and each test has the code it reads before its eyes.
class EntityExtractorTest {

    @UMLClass(name = "NomPersonnalise", stereotype = "Entity", color = "AABBCC")
    static class ClassWithoutMembers {}

    @UMLClass(color = "#ABC")
    static class ColorShortHex {}

    // Without strategy: the default is NONE, so publicField will not appear in the box.
    @UMLClass
    static class WithoutMetadata {
        public String publicField;
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class ClassWithMembers {
        public String publicField;
    }

    static class ClassNotAnnotated {}

    // --- Class metadata ---

    @Test
    void nameStereotypeAndColour_areCarriedOverToTheEntity() {
        UmlEntity entity = EntityExtractor.extract(ClassWithoutMembers.class);

        assertEquals("NomPersonnalise", entity.name());
        assertEquals("Entity", entity.stereotype());
        // The colour is taken as is, without the '#': adding it is the rendering's business.
        assertEquals("AABBCC", entity.color());
        // No strategy declared, hence no member — even if the class had some.
        assertTrue(entity.fields().isEmpty());
        assertTrue(entity.methods().isEmpty());
    }

    @Test
    void defaultName_isTheSimpleNameWhenNotOverridden() {
        UmlEntity entity = EntityExtractor.extract(WithoutMetadata.class);

        assertEquals("WithoutMetadata", entity.name());
    }

    @Test
    void qualifiedName_isTheIdentityOfTheEntity() {
        // The label is free, hence neither unique nor tied to the code; it is the qualified name that
        // lets a link designate its target unambiguously.
        UmlEntity entity = EntityExtractor.extract(ClassWithoutMembers.class);

        assertEquals(ClassWithoutMembers.class.getName(), entity.qualifiedName());
    }

    @Test
    void shortHexColour_isAccepted() {
        UmlEntity entity = EntityExtractor.extract(ColorShortHex.class);
        assertEquals("#ABC", entity.color(),
            "The 3-digit hexadecimal format must be accepted.");
    }

    // --- The catalogue of link extractors ---

    // Target of an association and of a dependency; it must carry @UMLClass, otherwise
    // TargetResolver would refuse to aim at it.
    @UMLClass
    static class Target {}

    @UMLClass
    @UMLDependency(target = Target.class, label = "calls")
    static class CarriesBothLinkNatures {
        @UMLAssociation(target = Target.class)
        private Target held;
    }

    @Test
    void everyLinkNature_arrivesInTheSameList() {
        // The orchestrator's own contract: each extractor of the catalogue is called, and
        // its links join the others. A forgotten extractor would show here, and nowhere
        // elsewhere — each of the specialized tests knows its own nature only.
        List<UmlRelation> relations =
            EntityExtractor.extract(CarriesBothLinkNatures.class).relations();

        // Message as a lambda: the string is built only if the test fails.
        assertEquals(2, relations.size(), () -> "Both links are expected: " + relations);
        assertEquals(1, relations.stream().filter(r -> r instanceof UmlAssociation).count());
        assertEquals(1, relations.stream().filter(r -> r instanceof UmlDependency).count());
    }

    @Test
    void linkOrder_followsTheCatalogueOrder() {
        // Associations before dependencies: the catalogue order is the one in which
        // the strokes will be written, hence a reproducible rendering decision.
        List<UmlRelation> relations =
            EntityExtractor.extract(CarriesBothLinkNatures.class).relations();

        assertTrue(relations.get(0) instanceof UmlAssociation, relations.toString());
        assertTrue(relations.get(1) instanceof UmlDependency, relations.toString());
    }

    // --- Guards ---

    @Test
    void extractionFailsIfTheClassIsNotAnnotated() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityExtractor.extract(ClassNotAnnotated.class));
    }

    @Test
    void extractionFailsOnNull() {
        assertThrows(NullPointerException.class, () -> EntityExtractor.extract(null));
    }

    // add(null) rather than a real UmlField: the immutable list refuses the addition even before
    // looking at what it is given.
    @Test
    void fieldsAreImmutableLists() {
        UmlEntity entity = EntityExtractor.extract(ClassWithMembers.class);

        assertThrows(UnsupportedOperationException.class, () -> entity.fields().add(null));
    }

    @Test
    void twoExtractionsOfTheSameClass_areEqual() {
        assertEquals(EntityExtractor.extract(ClassWithMembers.class),
            EntityExtractor.extract(ClassWithMembers.class),
            "UmlEntity is a record: two identical extractions must be equal.");
    }
}
