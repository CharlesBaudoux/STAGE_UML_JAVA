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

// Two relaxations reducing the annotation cost, measured on a real project:
// eleven lines of annotations for three trivial record boxes.
//
// A record component is the very definition of the record: it appears whatever the
// strategy, NONE included — that strategy is made to choose among ordinary
// ordinary ones, not to silence a definition. And an @UMLExclude set on the declaration of an
// an interface holds for each of its realizations: the member excludes itself where it is declared,
// once.
class RecordsAndInheritedExclusionsTest {

    // Without @UMLClass: the integration tests scan the whole of target/test-classes, and
    // MemberExtractor n'en a not besoin.
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

    // --- The include written on a component, and Java's ricochet ---

    record Renamed(@UMLInclude(name = "mnemonic") String keysymMnemonic) {}

    @Test
    void anIncludeOnAComponent_renamesTheField() {
        // The need warranting the "name" parameter: the reference diagram names the
        // things at greater length, or otherwise, than the code.
        List<UmlField> fields =
            MemberExtractor.extractFields(Renamed.class, UMLVisibility.NONE);

        assertEquals(1, fields.size(), fields.toString());
        assertEquals("mnemonic", fields.get(0).name());
    }

    @Test
    void anIncludeOnAComponent_doesNotAddTheAccessor() {
        // The compiler copies the annotation onto the field AND onto the accessor: @UMLInclude aims at
        // FIELD and METHOD. The accessor, included this way, appeared on top of the component — the box
        // montrait « mnemonic : String » and « mnemonic() : String » for one only intention,
        // with nothing reporting it.
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
        // The mirror of the previous test, and what makes the gap safe: here nothing propagated —
        // the field does not carry the annotation —, the author did aim at the method, and it stays.
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
        // Only the components are the definition of the record; an added static field stays an
        // a member like any other, which NONE silences.
        List<UmlField> fields =
            MemberExtractor.extractFields(WithConstant.class, UMLVisibility.NONE);

        assertEquals(1, fields.size(), fields.toString());
        assertEquals("component", fields.get(0).name());
    }

    // --- The exclusion set on the declaration, read from the realizations ---

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
        // The control companion: the accessor of the component is excluded nowhere.
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

    // --- The inherited exclusion crosses type erasure ---

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
        // The case exact-signature search missed: "Box<T>.file(T)" is written
        // "file(Object)" in the .class, while the realization carries String. The exclusion
        // was therefore lost for Comparator, Function, and every parameterized business interface — a
        // a member the author believes excluded reappeared on each of its realizations.
        //
        // "read()", without a parameter, was indeed excluded: that is what made the defect
        // invisible, the feature working on half the cases.
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

        // An overload, and not an override: the supertype never spoke of it.
        public void file(String text, int rank) {}
    }

    @Test
    void anOverloadOfDifferentArity_isNotTouched() {
        // Recognition is done on the name, the arity and assignability: an overload that
        // taking one more argument overrides nothing, and the exclusion must not follow it.
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(StrictFiler.class, UMLVisibility.PUBLIC);

        assertEquals(1, methods.size(), methods.toString());
        assertEquals(2, methods.get(0).parameters().size(), methods.toString());
    }

    static class Homonym {
        // Static: it overrides nothing, the exclusion of an inherited homonym does not aim at it.
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
