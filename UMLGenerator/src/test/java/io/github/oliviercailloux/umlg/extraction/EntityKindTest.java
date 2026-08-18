package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import org.junit.jupiter.api.Test;

class EntityKindTest {

    private static UmlEntityKind nature(Class<?> clazz) {
        return EntityExtractor.extract(clazz).kind();
    }

    @UMLClass
    static class Account {}

    @Test
    void ordinaryClass_isAClass() {
        assertEquals(UmlEntityKind.CLASS, nature(Account.class));
    }

    @UMLClass
    abstract static class Operation {}

    @Test
    void abstractClass_isRecognized() {
        assertEquals(UmlEntityKind.ABSTRACT_CLASS, nature(Operation.class));
    }

    @UMLClass
    interface Transfer {}

    @Test
    void interface_isRecognizedBeforeAbstraction() {
        assertEquals(UmlEntityKind.INTERFACE, nature(Transfer.class));
    }

    @UMLClass
    enum Currency {
        Euro, Dollar
    }

    @Test
    void enumeration_isRecognized() {
        assertEquals(UmlEntityKind.ENUM, nature(Currency.class));
    }

    @UMLClass
    enum Operator {
        PLUS {
            @Override
            int apply(int a, int b) {
                return a + b;
            }
        };

        abstract int apply(int a, int b);
    }

    @Test
    void abstractEnumeration_staysAnEnumeration() {
        assertEquals(UmlEntityKind.ENUM, nature(Operator.class));
    }

    @UMLClass
    @interface Auditable {}

    @Test
    void annotationType_isRecognizedBeforeTheInterface() {
        assertEquals(UmlEntityKind.ANNOTATION, nature(Auditable.class));
    }

    @UMLClass
    record Amount(double value, String currency) {}

    @Test
    void record_isAClass() {
        assertEquals(UmlEntityKind.CLASS, nature(Amount.class));
    }
}
