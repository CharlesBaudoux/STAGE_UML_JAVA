package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

// The header of a generic box, and the italics of abstract operations.
//
// Two gaps that looked alike: reflection knows both things, and the model had
// no component to say them. A "Box<T>" was displayed as "Box", with a field of
// a type T nothing defined any more; and in an abstract class, a contract was drawn
// exactly like an implementation.
class GenericHeaderTest {

    @UMLClass
    static class Box<T> {
        public T content;
    }

    @UMLClass
    static class Table<K, V extends Number> {}

    @UMLClass
    static class Ordinary {}

    @UMLClass
    abstract static class Contract {
        public abstract void validate();

        public void written() {}
    }

    @UMLClass
    interface Signature {
        void mandatory();

        default void provided() {}
    }

    private static UmlEntity extracted(Class<?> clazz) {
        return EntityExtractor.extract(clazz, Rules.read("""
            { "strategy": "public" }
            """,
            "umlg.json"));
    }

    @Test
    void aGenericClass_carriesItsTypeParameter() {
        assertEquals("<T>", extracted(Box.class).typeParameters());
    }

    @Test
    void severalParameters_areWrittenInOrderAndWithTheirBounds() {
        assertEquals("<K, V extends Number>", extracted(Table.class).typeParameters());
    }

    @Test
    void anOrdinaryClass_carriesNone() {
        // The empty string, and not null: the header must gain nothing over a class that is
        // not generic.
        assertEquals("", extracted(Ordinary.class).typeParameters());
    }

    @Test
    void theTypeParameter_doesNotChangeTheBoxName() {
        // The name serves to designate the box — a view names it so, the alias follows. Sticking
        // the angle brackets inside would make "view \"X\" = Box" unfindable.
        assertEquals("Box", extracted(Box.class).name());
    }

    private static UmlMethod method(Class<?> clazz, String name) {
        return extracted(clazz).methods().stream()
            .filter(method -> method.name().equals(name))
            .findFirst().orElseThrow();
    }

    @Test
    void aMethodWithoutBody_isSaidAbstract() {
        assertTrue(method(Contract.class, "validate").isAbstract());
    }

    @Test
    void aWrittenMethod_isNot() {
        assertFalse(method(Contract.class, "written").isAbstract());
    }

    @Test
    void anInterfaceMethod_isAbstractWithoutCarryingTheKeyword() {
        assertTrue(method(Signature.class, "mandatory").isAbstract());
    }

    @Test
    void aDefaultMethod_isNot() {
        // "default" is precisely what tells apart, in an interface, the contract from what
        // is provided: the modifier does not lie, unlike the missing keyword.
        assertFalse(method(Signature.class, "provided").isAbstract());
    }

    @Test
    void theOrdinaryModel_buildsConcreteMethods() {
        // The seven-argument shortcut, the one the rendering tests use: abstraction
        // is the exception, and not saying it means there is none.
        UmlMethod method = new UmlMethod("do", "void", "+", false, List.of());

        assertFalse(method.isAbstract());
    }
}
