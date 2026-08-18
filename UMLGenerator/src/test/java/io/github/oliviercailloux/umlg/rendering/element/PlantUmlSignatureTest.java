package io.github.oliviercailloux.umlg.rendering.element;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantUmlSignatureTest {

    @UMLClass(name = "Flight", strategy = UMLVisibility.PRIVATE)
    static class Flight {
        private String departureTime;

        public void reporter(String newTime, int minutes) {}

        public String destination() {
            return "";
        }

        @UMLDerived
        public static int count(String code) {
            return 0;
        }
    }

    private static String source() {
        return PlantUmlAssert.documentFor(EntityExtractor.extract(Flight.class));
    }

    @Test
    void field_isWrittenNameThenType() {
        assertTrue(source().contains("- departureTime : String"), source());
    }

    @Test
    void method_writesItsReturnTypeAfterTheParentheses() {
        assertTrue(source().contains("+ destination() : String"), source());
    }

    @Test
    void parameters_carryTheirNameWhenCompilationKeepsIt() {
        assertTrue(source().contains("+ reporter(newTime : String, minutes : int) : void"),
            "The parameters must be named and separated by a comma:\n" + source());
        assertFalse(source().contains("arg0"), source());
    }

    @Test
    void staticAndDerived_keepTheirNotationsAroundTheSignature() {
        assertTrue(source().contains("+ {static} /count(code : String) : int"), source());
        PlantUmlAssert.assertCompile(source());
    }

    @Test
    void parameterWithoutName_showsItsTypeOnly() {
        UmlEntity entity = UmlEntity.builder("com.example.Account", "Account")
            .methods(List.of(new UmlMethod("remove", "void", "", "+", false,
                ImmutableList.of(UmlParameter.unnamed("double"), new UmlParameter("pattern",
                    "String")),
                false)))
            .build();

        String source = PlantUmlAssert.documentFor(entity);

        assertTrue(source.contains("+ remove(double, pattern : String) : void"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void readerSeesTheCompleteSignature() throws Exception {
        String text = PlantUmlAssert.textRendered(EntityExtractor.extract(Flight.class));

        assertTrue(text.contains("departureTime : String"), text);
        assertTrue(text.contains("reporter(newTime : String, minutes : int) : void"), text);
    }
}
