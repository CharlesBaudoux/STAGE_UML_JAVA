package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordsAndInheritedExclusionsTest {

    record Mnemonic(String keysymMnemonic) implements Entry {
        @Override
        public String asString() {
            return keysymMnemonic;
        }
    }

    @Test
    void aRecordComponent_appearsEvenUnderStrategyNone() {
        List<UmlField> fields =
            MemberExtractor.extractFields(Mnemonic.class, UMLVisibility.NONE);

        assertEquals(1, fields.size(), fields.toString());
        assertEquals("keysymMnemonic", fields.get(0).name());
        assertEquals("String", fields.get(0).type());
    }

    record Renamed(@UMLInclude(name = "mnemonic") String keysymMnemonic) {}

    @Test
    void anIncludeOnAComponent_renamesTheField() {
        List<UmlField> fields =
            MemberExtractor.extractFields(Renamed.class, UMLVisibility.NONE);

        assertEquals(1, fields.size(), fields.toString());
        assertEquals("mnemonic", fields.get(0).name());
    }

    @Test
    void anIncludeOnAComponent_doesNotAddTheAccessor() {
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(Renamed.class, UMLVisibility.NONE);

        assertTrue(methods.isEmpty(), "The accessor must not duplicate the component: "
            + methods);
    }

    record AccessorWritten(String component) {
        @UMLInclude(name = "lu")
        public String component() {
            return component;
        }
    }

    @Test
    void anIncludeOnAHandWrittenAccessor_isRespected() {
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(AccessorWritten.class, UMLVisibility.NONE);

        assertEquals(1, methods.size(), methods.toString());
        assertEquals("lu", methods.get(0).name());
    }

    record Hidden(String visible, @UMLExclude String tu) {}

    @Test
    void anExcludedComponent_vanishes_theIndividualVetoHasTheLastWord() {
        List<UmlField> fields =
            MemberExtractor.extractFields(Hidden.class, UMLVisibility.NONE);

        assertEquals(1, fields.size(), fields.toString());
        assertEquals("visible", fields.get(0).name());
    }

    record WithConstant(String component) {
        static final int Threshold = 3;
    }

    @Test
    void aFieldAddedToTheRecord_followsTheOrdinaryStrategy() {
        List<UmlField> fields =
            MemberExtractor.extractFields(WithConstant.class, UMLVisibility.NONE);

        assertEquals(1, fields.size(), fields.toString());
        assertEquals("component", fields.get(0).name());
    }

    interface Entry {
        @UMLExclude
        String asString();
    }

    @Test
    void anExcludeOnTheInterface_holdsForTheOverride() {
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(Mnemonic.class, UMLVisibility.PUBLIC);

        assertTrue(methods.stream().noneMatch(m -> m.name().equals("asString")),
            "asString is excluded on the declaration of Entry; the override in Mnemonic must "
                + "silence without repeating the annotation: " + methods);
        assertTrue(methods.stream().anyMatch(m -> m.name().equals("keysymMnemonic")),
            methods.toString());
    }

    abstract static class Parent {
        @UMLExclude
        public void technical() {}
    }

    static class Child extends Parent {
        @Override
        public void technical() {}

        public void business() {}
    }

    @Test
    void anExcludeOnTheParent_holdsForTheChild() {
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(Child.class, UMLVisibility.PUBLIC);

        assertTrue(methods.stream().noneMatch(m -> m.name().equals("technical")),
            methods.toString());
        assertTrue(methods.stream().anyMatch(m -> m.name().equals("business")),
            methods.toString());
    }

    interface Box<T> {
        @UMLExclude
        void file(T object);

        @UMLExclude
        T read();
    }

    static class TextBox implements Box<String> {
        @Override
        public void file(String object) {}

        @Override
        public String read() {
            return "";
        }

        public void business() {}
    }

    @Test
    void anExcludeOnAGenericInterface_holdsForTheRealization() {
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(TextBox.class, UMLVisibility.PUBLIC);

        assertEquals(List.of("business"), methods.stream().map(UmlMethod::name).toList(),
            methods.toString());
    }

    interface Filer {
        @UMLExclude
        void file(Object object);
    }

    static class StrictFiler implements Filer {
        @Override
        public void file(Object object) {}

        public void file(String text, int rank) {}
    }

    @Test
    void anOverloadOfDifferentArity_isNotTouched() {
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(StrictFiler.class, UMLVisibility.PUBLIC);

        assertEquals(1, methods.size(), methods.toString());
        assertEquals(2, methods.get(0).parameters().size(), methods.toString());
    }

    static class Homonym {
        public static void technical() {}
    }

    @Test
    void aHomonymousStatic_overridesNothing_andStaysVisible() {
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(Homonym.class, UMLVisibility.PUBLIC);

        assertTrue(methods.stream().anyMatch(m -> m.name().equals("technical")),
            methods.toString());
    }
}
