package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlantUmlInheritanceTest {

    private static UmlEntity box(String qualifiedName, String name, UmlEntityKind kind) {
        return UmlEntity.builder(qualifiedName, name).kind(kind).build();
    }

    private static UmlEntity child(String name, UmlInheritance... inheritances) {
        return UmlEntity.builder("pkg." + name, name)
            .relations(ImmutableList.copyOf(inheritances))
            .build();
    }

    @Test
    void generalization_givesASolidStroke() {
        UmlEntity parent = box("pkg.Vehicle", "Vehicle", UmlEntityKind.CLASS);
        UmlEntity car = child("Car", UmlInheritance.generalization("pkg.Vehicle"));

        assertEquals(List.of("Vehicle <|-- Car"),
            PlantUmlAssert.linkLines(PlantUmlAssert.documentFor(parent, car)));
    }

    @Test
    void realization_givesADottedStroke() {
        UmlEntity rolling = box("pkg.Rolling", "Rolling", UmlEntityKind.INTERFACE);
        UmlEntity truck = child("Truck", UmlInheritance.realization("pkg.Rolling"));

        assertEquals(List.of("Rolling <|.. Truck"),
            PlantUmlAssert.linkLines(PlantUmlAssert.documentFor(rolling, truck)));
    }

    @Test
    void theTargetIsWrittenFirst() {
        UmlEntity parent = box("pkg.Vehicle", "Vehicle", UmlEntityKind.CLASS);
        UmlEntity car = child("Car", UmlInheritance.generalization("pkg.Vehicle"));

        String line = PlantUmlAssert.linkLines(
            PlantUmlAssert.documentFor(parent, car)).get(0);

        assertTrue(line.indexOf("Vehicle") < line.indexOf("Car"), line);
    }

    @Test
    void inheritance_receivesNoLabel() {
        UmlEntity parent = box("pkg.Vehicle", "Vehicle", UmlEntityKind.CLASS);
        UmlEntity car = child("Car", UmlInheritance.generalization("pkg.Vehicle"));

        assertTrue(PlantUmlAssert.linkLines(PlantUmlAssert.documentFor(parent, car))
                                 .get(0).indexOf(':') < 0);
    }

    @Test
    void severalInheritances_giveAsManyLines() {
        UmlEntity rolling = box("pkg.Rolling", "Rolling", UmlEntityKind.INTERFACE);
        UmlEntity vehicle = box("pkg.Vehicle", "Vehicle", UmlEntityKind.ABSTRACT_CLASS);
        UmlEntity bus = child("Bus",
            UmlInheritance.generalization("pkg.Vehicle"),
            UmlInheritance.realization("pkg.Rolling"));

        assertEquals(List.of("Vehicle <|-- Bus", "Rolling <|.. Bus"),
            PlantUmlAssert.linkLines(PlantUmlAssert.documentFor(rolling, vehicle, bus)));
    }

    @ParameterizedTest
    @CsvSource({
        "CLASS,           class",
        "ABSTRACT_CLASS,  abstract class",
        "INTERFACE,       interface",
        "ENUM,            enum",
        "ANNOTATION,      annotation"
    })
    void entityNature_choosesTheKeyword(UmlEntityKind kind, String wordKey) {
        String source = PlantUmlAssert.documentFor(box("pkg.E", "E", kind));

        assertTrue(source.contains(wordKey + " \"E\" as E {"), source);
    }

    @Test
    void everyNature_isAcceptedByPlantUml() {
        UmlEntity[] boxes = new UmlEntity[UmlEntityKind.values().length];
        int i = 0;
        for (UmlEntityKind kind : UmlEntityKind.values()) {
            boxes[i] = box("pkg." + kind.name(), kind.name(), kind);
            i++;
        }

        PlantUmlAssert.assertCompile(PlantUmlAssert.documentFor(boxes));
    }

    @Test
    void completeHierarchy_isAcceptedByPlantUml() {
        UmlEntity rolling = box("pkg.Rolling", "Rolling", UmlEntityKind.INTERFACE);
        UmlEntity vehicle = box("pkg.Vehicle", "Vehicle", UmlEntityKind.ABSTRACT_CLASS);
        UmlEntity bus = child("Bus",
            UmlInheritance.generalization("pkg.Vehicle"),
            UmlInheritance.realization("pkg.Rolling"));

        PlantUmlAssert.assertCompile(PlantUmlAssert.documentFor(rolling, vehicle, bus));
    }

    @Test
    void undeclaredTarget_isReportedAsAnInheritance() {
        UmlEntity orphan = child("Orphan", UmlInheritance.generalization("pkg.Missing"));

        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class, () -> PlantUmlAssert.documentFor(orphan));

        assertTrue(e.getMessage().contains("generalization"), e.getMessage());
        assertTrue(e.getMessage().contains("pkg.Missing"), e.getMessage());
    }
}
