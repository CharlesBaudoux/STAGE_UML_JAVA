package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import org.junit.jupiter.api.Test;

// The nature of the entity, inferred from the Java keyword.
//
// What is at stake in these tests is the order of the questions put to reflection: the Java categories
// overlap, and each fixture below belongs to several of them at once.
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
        // An interface always carries the abstract modifier: if the abstraction test
        // came first, every interface would become an abstract class.
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
        // A constant with a body makes the enumeration an abstract class for the compiler.
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
        // The most telling fixture: Operator is both enum and abstract. The test on
        // the enumeration must therefore come before the one on abstraction.
        assertEquals(UmlEntityKind.ENUM, nature(Operator.class));
    }

    @UMLClass
    @interface Auditable {}

    @Test
    void annotationType_isRecognizedBeforeTheInterface() {
        // An @interface is an interface for reflection: with no prior test, it would be
        // drawn as such, and the information would be lost.
        assertEquals(UmlEntityKind.ANNOTATION, nature(Auditable.class));
    }

    @UMLClass
    record Amount(double value, String currency) {}

    @Test
    void record_isAClass() {
        // A deliberate decision: a record is a final class, and UML reserves it no
        // notation. Le dessiner en class n'is not un pis-aller, c'is exact.
        assertEquals(UmlEntityKind.CLASS, nature(Amount.class));
    }
}
