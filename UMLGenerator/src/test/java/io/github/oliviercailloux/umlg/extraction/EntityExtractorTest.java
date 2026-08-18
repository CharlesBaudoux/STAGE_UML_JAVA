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

class EntityExtractorTest {

    @UMLClass(name = "NomPersonnalise", stereotype = "Entity", color = "AABBCC")
    static class ClassWithoutMembers {}

    @UMLClass(color = "#ABC")
    static class ColorShortHex {}

    @UMLClass
    static class WithoutMetadata {
        public String publicField;
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class ClassWithMembers {
        public String publicField;
    }

    static class ClassNotAnnotated {}

    @Test
    void nameStereotypeAndColour_areCarriedOverToTheEntity() {
        UmlEntity entity = EntityExtractor.extract(ClassWithoutMembers.class);

        assertEquals("NomPersonnalise", entity.name());
        assertEquals("Entity", entity.stereotype());
        assertEquals("AABBCC", entity.color());
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
        UmlEntity entity = EntityExtractor.extract(ClassWithoutMembers.class);

        assertEquals(ClassWithoutMembers.class.getName(), entity.qualifiedName());
    }

    @Test
    void shortHexColour_isAccepted() {
        UmlEntity entity = EntityExtractor.extract(ColorShortHex.class);
        assertEquals("#ABC", entity.color(),
            "The 3-digit hexadecimal format must be accepted.");
    }

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
        List<UmlRelation> relations =
            EntityExtractor.extract(CarriesBothLinkNatures.class).relations();

        assertEquals(2, relations.size(), () -> "Both links are expected: " + relations);
        assertEquals(1, relations.stream().filter(r -> r instanceof UmlAssociation).count());
        assertEquals(1, relations.stream().filter(r -> r instanceof UmlDependency).count());
    }

    @Test
    void linkOrder_followsTheCatalogueOrder() {
        List<UmlRelation> relations =
            EntityExtractor.extract(CarriesBothLinkNatures.class).relations();

        assertTrue(relations.get(0) instanceof UmlAssociation, relations.toString());
        assertTrue(relations.get(1) instanceof UmlDependency, relations.toString());
    }

    @Test
    void extractionFailsIfTheClassIsNotAnnotated() {
        assertThrows(IllegalArgumentException.class,
            () -> EntityExtractor.extract(ClassNotAnnotated.class));
    }

    @Test
    void extractionFailsOnNull() {
        assertThrows(NullPointerException.class, () -> EntityExtractor.extract(null));
    }

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
