package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemberExtractorTest {

    private static Optional<UmlField> field(UmlEntity e, String name) {
        return e.fields().stream().filter(f -> f.name().equals(name)).findFirst();
    }

    private static List<UmlMethod> methods(UmlEntity e, String name) {
        return e.methods().stream().filter(m -> m.name().equals(name)).toList();
    }

    private static Optional<UmlMethod> method(UmlEntity e, String name) {
        return methods(e, name).stream().findFirst();
    }

    private static List<String> types(UmlMethod method) {
        return method.parameters().stream().map(UmlParameter::type).toList();
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    enum Statut {
        Active, Suspended;

        private String label;

        public void toggle() {}
    }

    @Test
    void enum_doesNotExposeTheSyntheticValuesField() {
        UmlEntity entity = EntityExtractor.extract(Statut.class);

        assertTrue(field(entity, "$VALUES").isEmpty(),
            "The synthetic $VALUES field must not appear: " + entity.fields());
    }

    @Test
    void enum_exposesItsConstantsAndItsBusinessFields() {
        UmlEntity entity = EntityExtractor.extract(Statut.class);

        assertTrue(entity.literals().contains("Active"),
            "The enum constants are expected among the literals: " + entity.literals());
        assertTrue(field(entity, "Active").isEmpty(),
            "An enum constant must no longer appear among the fields: " + entity.fields());
        assertTrue(field(entity, "label").isPresent());
        assertTrue(method(entity, "toggle").isPresent());
    }

    @Test
    void instrumentedClass_doesNotLeakItsSyntheticFields() {
        UmlEntity entity = EntityExtractor.extract(Statut.class);

        assertTrue(entity.fields().stream().noneMatch(f -> f.name().startsWith("$")),
            "No synthetic field must appear: " + entity.fields());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    record Coordinates(double latitude, double longitude) {
        public double distance(Coordinates other) {
            return 0;
        }
    }

    @Test
    void record_exposesItsComponentsAsFields() {
        UmlEntity entity = EntityExtractor.extract(Coordinates.class);

        assertTrue(field(entity, "latitude").isPresent());
        assertEquals("double", field(entity, "longitude").orElseThrow().type());
        assertTrue(method(entity, "distance").isPresent(), "The business method must stay.");
    }

    @Test
    void noSyntheticMethod_comesUp() {
        UmlEntity entity = EntityExtractor.extract(Coordinates.class);

        assertFalse(entity.methods().stream().anyMatch(m -> m.name().contains("$")),
            "Synthetic methods (lambdas, bridges) must not appear: "
                + entity.methods());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    interface Payment {
        void payer(int amount);
    }

    @Test
    void interface_exposesItsMethodsAsPublic() {
        UmlEntity entity = EntityExtractor.extract(Payment.class);

        UmlMethod payer = method(entity, "payer").orElseThrow();
        assertEquals("+", payer.visibility(),
            "An interface method is implicitly public.");
        assertEquals(List.of("int"), types(payer));
    }

    static class Ancestor {
        public String inheritedField;

        public void inheritedMethod() {}
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Descendant extends Ancestor {
        private String ownField;

        public void ownMethod() {}
    }

    @Test
    void inheritance_onlyDeclaredMembersAreExtracted() {
        UmlEntity entity = EntityExtractor.extract(Descendant.class);

        assertTrue(field(entity, "ownField").isPresent());
        assertTrue(field(entity, "inheritedField").isEmpty(),
            "An inherited field belongs to the parent class, not to the descendant.");
        assertTrue(method(entity, "ownMethod").isPresent());
        assertTrue(method(entity, "inheritedMethod").isEmpty());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Overloads {
        public void send() {}

        public void send(String message) {}

        public void send(String message, int priority) {}
    }

    @Test
    void overloads_areAllKept() {
        UmlEntity entity = EntityExtractor.extract(Overloads.class);

        List<UmlMethod> send = methods(entity, "send");
        assertEquals(3, send.size(), "The 3 overloads must be present: " + send);

        List<List<String>> signatures =
            send.stream().map(MemberExtractorTest::types).toList();
        assertTrue(signatures.contains(List.of()), signatures.toString());
        assertTrue(signatures.contains(List.of("String")), signatures.toString());
        assertTrue(signatures.contains(List.of("String", "int")), signatures.toString());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class MethodWithParameters {
        public void compute(int a, String b) {}
    }

    @Test
    void methodParameters_areExtractedInOrder() {
        UmlEntity entity = EntityExtractor.extract(MethodWithParameters.class);

        UmlMethod compute = method(entity, "compute").orElseThrow();
        assertEquals(List.of("int", "String"), types(compute));
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class TypesVaried {
        public int[] array;
        public List<String> list;
        public String text;

        public List<String> list(int[] entries) {
            return List.of();
        }
    }

    @Test
    void arrayAndGenericTypes_keepTheirTypeParameters() {
        UmlEntity entity = EntityExtractor.extract(TypesVaried.class);

        assertEquals("int[]", field(entity, "array").orElseThrow().type(),
            "An array must keep its brackets.");
        assertEquals("List<String>", field(entity, "list").orElseThrow().type());

        UmlMethod list = method(entity, "list").orElseThrow();
        assertEquals("List<String>", list.returnType());
        assertEquals(List.of("int[]"), types(list));
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class MethodVarargs {
        public void log(String format, Object... arguments) {}

        public void copy(int[] source) {}
    }

    @Test
    void aVarargsParameter_isWrittenWithThreeDots() {
        UmlEntity entity = EntityExtractor.extract(MethodVarargs.class);

        assertEquals(List.of("String", "Object..."),
            types(method(entity, "log").orElseThrow()));
        assertEquals(List.of("int[]"), types(method(entity, "copy").orElseThrow()));
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class ClassWithStatic {
        public static final String Constant = "x";
        public static void staticMethod() {}
    }

    @Test
    void staticMembers_areMarkedAsSuch() {
        UmlEntity entity = EntityExtractor.extract(ClassWithStatic.class);

        UmlField constant = field(entity, "Constant").orElseThrow();
        assertTrue(constant.isStatic());

        UmlMethod staticMethod = method(entity, "staticMethod").orElseThrow();
        assertTrue(staticMethod.isStatic());
    }

    @Test
    void memberOrder_isStableBetweenTwoExtractions() {
        UmlEntity a = EntityExtractor.extract(Overloads.class);
        UmlEntity b = EntityExtractor.extract(Overloads.class);

        assertEquals(a.methods(), b.methods());
        assertEquals(a.fields(), b.fields());
    }
}
