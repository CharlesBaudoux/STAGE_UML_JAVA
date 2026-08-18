package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyExtractorTest {

    @UMLClass
    static class ServiceBank {}

    @UMLClass
    static class Log {}

    static class OutsideDiagram {}

    @UMLClass
    @UMLDependency(target = ServiceBank.class, label = "invoice via")
    static class Cart {}

    @Test
    void simpleDependency_isExtracted() {
        UmlEntity entity = EntityExtractor.extract(Cart.class);

        assertEquals(
            ImmutableList.of(new UmlDependency(ServiceBank.class.getName(), "invoice via")),
            entity.dependencies());
    }

    @UMLClass
    @UMLDependency(target = ServiceBank.class)
    static class WithoutPattern {}

    @Test
    void omittedLabel_leavesTheLinkBare() {
        assertEquals("", EntityExtractor.extract(WithoutPattern.class).dependencies().get(0).label());
    }

    @UMLClass
    @UMLDependency(target = ServiceBank.class, label = "pays")
    @UMLDependency(target = Log.class, label = "trace")
    static class TwoDependencies {}

    @Test
    void repeatedAnnotation_producesAsManyLinks() {
        List<UmlDependency> dependencies =
            EntityExtractor.extract(TwoDependencies.class).dependencies();

        assertEquals(2, dependencies.size());
        assertEquals(
            List.of(ServiceBank.class.getName(), Log.class.getName()),
            dependencies.stream().map(UmlDependency::targetQualifiedName).toList());
    }

    @UMLClass
    @UMLDependency(target = ServiceBank.class, label = "pays")
    @UMLDependency(target = ServiceBank.class, label = "rembourse")
    static class SameTargetTwoPatterns {}

    @Test
    void sameTargetWithDifferentReasons_isAccepted() {
        assertEquals(2, EntityExtractor.extract(SameTargetTwoPatterns.class).dependencies().size());
    }

    @UMLClass
    static class NoDependency {}

    @Test
    void withoutAnnotation_noDependencyIsInferred() {
        assertTrue(EntityExtractor.extract(NoDependency.class).dependencies().isEmpty());
    }

    @UMLDependency(target = ServiceBank.class, label = "pays")
    @UMLDependency(target = ServiceBank.class, label = "pays")
    static class ExactDuplicate {}

    @Test
    void dependencyDeclaredTwiceIdentically_isRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new DependencyExtractor().extract(ExactDuplicate.class));

        assertTrue(e.getMessage().contains("twice"),
            () -> "The message must name the fault: " + e.getMessage());
    }

    @UMLDependency(target = AutoDependency.class)
    static class AutoDependency {}

    @Test
    void classDependingOnItself_isRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new DependencyExtractor().extract(AutoDependency.class));

        assertTrue(e.getMessage().contains("depend on itself"),
            () -> "The message must name the fault: " + e.getMessage());
    }

    @UMLDependency(target = OutsideDiagram.class)
    static class TargetNotAnnotated {}

    @Test
    void targetWithoutUmlClass_isRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new DependencyExtractor().extract(TargetNotAnnotated.class));

        assertTrue(e.getMessage().contains("@UMLClass") && e.getMessage().contains("target()"),
            () -> "The message must explain what is missing and how to fix it: "
                + e.getMessage());
    }

    @UMLDependency(target = int.class)
    static class PrimitiveTarget {}

    @Test
    void primitiveTarget_isRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new DependencyExtractor().extract(PrimitiveTarget.class));
    }

    @UMLDependency(target = ServiceBank.class, label = "   ")
    static class BlankLabel {}

    @Test
    void blankLabel_isRefusedNamingTheClass() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new DependencyExtractor().extract(BlankLabel.class));

        assertTrue(e.getMessage().contains("BlankLabel"),
            () -> "The message must locate the fault: " + e.getMessage());
    }

    @Test
    void dependencyList_isImmutable() {
        List<UmlDependency> dependencies = EntityExtractor.extract(Cart.class).dependencies();

        assertThrows(UnsupportedOperationException.class, () -> dependencies.clear());
    }
}
