package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PlantUmlDependencyTest {

    @UMLClass
    static class ServiceBank {}

    private static UmlEntity target(String qualifiedName) {
        return UmlEntity.builder(qualifiedName, "Target").build();
    }

    private static UmlEntity[] pair(UmlDependency dependency) {
        UmlEntity source = UmlEntity.builder("Source", "Source")
            .dependencies(ImmutableList.of(dependency)).build();
        return new UmlEntity[] {source, target(dependency.targetQualifiedName())};
    }

    private static List<String> linkLines(String source) {
        return PlantUmlAssert.linkLines(source);
    }

    private static String lineUnique(String source) {
        List<String> lines = linkLines(source);
        assertEquals(1, lines.size(), () -> "Expected a single link in:\n" + source);
        return lines.get(0);
    }

    @Test
    void dependency_isADottedArrow() {
        assertEquals("Source ..> Target",
            lineUnique(PlantUmlAssert.documentFor(pair(UmlDependency.toTarget("Target")))));
    }

    @Test
    void label_followsTheColon() {
        assertEquals("Source ..> Target : invoice via",
            lineUnique(PlantUmlAssert.documentFor(
                pair(new UmlDependency("Target", "invoice via")))));
    }

    @Test
    void dependencyAndAssociation_coexistWithoutMerging() {
        UmlEntity source = UmlEntity.builder("Source", "Source")
            .associations(ImmutableList.of(UmlAssociation.builder("field", "Target").build()))
            .dependencies(ImmutableList.of(UmlDependency.toTarget("Target")))
            .build();

        String rendered = PlantUmlAssert.documentFor(source, target("Target"));

        assertEquals(List.of("Source --> Target", "Source ..> Target"), linkLines(rendered));
        PlantUmlAssert.assertCompile(rendered);
    }

    @Test
    void severalDependencies_produceAsManyStrokes() {
        UmlEntity source = UmlEntity.builder("Source", "Source")
            .dependencies(ImmutableList.of(
                new UmlDependency("A", "pays"), new UmlDependency("B", "trace")))
            .build();

        String rendered = PlantUmlAssert.documentFor(
            source, UmlEntity.builder("A", "A").build(), UmlEntity.builder("B", "B").build());

        assertEquals(List.of("Source ..> A : pays", "Source ..> B : trace"), linkLines(rendered));
        PlantUmlAssert.assertCompile(rendered);
    }

    static List<UmlDependency> dependenciesVaried() {
        return List.of(
            UmlDependency.toTarget("Target"),
            new UmlDependency("Target", "uses"),
            new UmlDependency("Target", "n'appelle qu'en lecture"),
            new UmlDependency("Target", "Ref. no.42"));
    }

    @ParameterizedTest
    @MethodSource("dependenciesVaried")
    void renderedDependency_isAcceptedByTheEngine(UmlDependency dependency) {
        String source = PlantUmlAssert.documentFor(pair(dependency));

        PlantUmlAssert.assertCompile(source);
        assertEquals(2, PlantUmlAssert.aliasDeclared(source).size(),
            () -> "The link made a box appear or vanish:\n" + source);
    }

    static List<String> labelsHostile() {
        return List.of(
            "end\" as X\nclass Intruse",
            "<img:/etc/hostname>",
            "@enduml\n@startuml\nclass Intruse",
            "}\nclass Intruse {",
            "Source ..> Intruse",
            "C:\\Users\\moi");
    }

    @ParameterizedTest
    @MethodSource("labelsHostile")
    void hostileLabel_breaksNeitherTheDocumentNorTheBoxCount(String label) {
        String source = PlantUmlAssert.documentFor(pair(new UmlDependency("Target", label)));

        assertEquals(1, PlantUmlAssert.diagramsCount(source),
            () -> "Le label hostile a open un second diagram :\n" + source);
        assertEquals(2, PlantUmlAssert.aliasDeclared(source).size(),
            () -> "The hostile label declared an extra class:\n" + source);
        assertEquals(1, linkLines(source).size(),
            () -> "The hostile label ended the link line:\n" + source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void anyLabel_isDisplayedAsIs() throws Exception {
        String rendered =
            PlantUmlAssert.textRendered(pair(new UmlDependency("Target", "invoice (n°1)")));

        assertTrue(rendered.contains("invoice (n°1)"),
            () -> "The label is not rendered faithfully. Drawn text:\n" + rendered);
    }

    @Test
    void targetAbsentFromTheDiagram_isAPlainError() {
        UmlEntity lone = UmlEntity.builder("Source", "Source")
            .dependencies(ImmutableList.of(UmlDependency.toTarget("Target"))).build();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PlantUmlAssert.documentFor(lone));

        assertTrue(e.getMessage().contains("Target") && e.getMessage().contains("dependency"),
            () -> "The message must name the faulty link: " + e.getMessage());
    }

    @Test
    void targetResolvedOnTheQualifiedName_andNotOnTheDisplayedName() {
        UmlEntity homonym = target("pkg.a.Target");
        UmlEntity targeted = target("pkg.b.Target");
        UmlEntity source = UmlEntity.builder("Source", "Source")
            .dependencies(ImmutableList.of(UmlDependency.toTarget("pkg.b.Target"))).build();

        String rendered = PlantUmlAssert.documentFor(homonym, targeted, source);
        List<String> alias = PlantUmlAssert.aliasDeclared(rendered);

        assertEquals("Source ..> " + alias.get(1), lineUnique(rendered));
        PlantUmlAssert.assertCompile(rendered);
    }

    @UMLClass
    @UMLDependency(target = ServiceBank.class, label = "invoice via")
    static class Cart {
        @UMLAssociation(target = ServiceBank.class, targetMultiplicity = "0..1")
        private ServiceBank lastPayment;
    }

    @Test
    void specificationExample_isRenderedAndAccepted() {
        String source = PlantUmlAssert.documentFor(
            EntityExtractor.extract(Cart.class), EntityExtractor.extract(ServiceBank.class));

        assertEquals(
            List.of("Cart --> \"0..1\" ServiceBank",
                "Cart ..> ServiceBank : invoice via"),
            linkLines(source));
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void declaredDependencies_appearAfterEveryClass() {
        String source = PlantUmlAssert.documentFor(
            EntityExtractor.extract(Cart.class), EntityExtractor.extract(ServiceBank.class));

        assertTrue(source.indexOf("..>") > source.lastIndexOf("class \""),
            () -> "A link quoted before the declaration of its target would make a box appear "
                + "empty :\n" + source);
    }
}
