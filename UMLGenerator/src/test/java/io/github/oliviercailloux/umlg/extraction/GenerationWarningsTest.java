package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GenerationWarningsTest {

    private static Rules rules(String json) {
        return Rules.read(json, "umlg.json");
    }

    @UMLClass
    static class Silent {
        public int field;

        public void perform() {}
    }

    @UMLClass
    static class Marker {}

    private static boolean emptyBox(Class<?> clazz, Rules rules) {
        return ProjectScanner.emptyBoxForLackOfStrategy(clazz,
            EntityExtractor.extract(clazz, rules), rules);
    }

    @Test
    void withoutStrategy_anEmptyBoxIsReported() {
        assertTrue(emptyBox(Silent.class, Rules.no()));
    }

    @Test
    void writtenStrategyNone_silencesTheReport() {
        assertFalse(emptyBox(Silent.class, rules("""
            { "strategy": "none" }
            """)));
    }

    @Test
    void aStrategyThatFills_hasNothingLeftToReport() {
        assertFalse(emptyBox(Silent.class, rules("""
            { "strategy": "public" }
            """)));
    }

    @Test
    void aClassWithoutMembers_isNotReported() {
        assertFalse(emptyBox(Marker.class, Rules.no()));
    }

    @Test
    void theReport_namesTheBoxes() {
        assertTrue(ProjectScanner.reportEmptyBoxes(ImmutableList.of("Silent")));
        assertFalse(ProjectScanner.reportEmptyBoxes(ImmutableList.of()));
    }

    private static UmlEntity entity(UmlMethod... methods) {
        return UmlEntity.builder("com.example.Item", "Item")
            .methods(List.of(methods)).build();
    }

    private static UmlMethod anonymousMethod() {
        return new UmlMethod("configure", "void", "+", false, List.of("String"));
    }

    private static UmlMethod namedMethod() {
        return new UmlMethod("configure", "void", "", "+", false,
            ImmutableList.of(new UmlParameter("path", "String")), false);
    }

    @Test
    void signaturesAllSilent_areReported() {
        assertTrue(ProjectScanner.reportAnonymousParameters(
            List.of(entity(anonymousMethod())), Rules.no()));
    }

    @Test
    void aSingleNamePresent_isEnoughToSilenceTheReport() {
        assertFalse(ProjectScanner.reportAnonymousParameters(
            List.of(entity(anonymousMethod()), entity(namedMethod())), Rules.no()));
    }

    @Test
    void namesHiddenByTheRule_areNotReported() {
        assertFalse(ProjectScanner.reportAnonymousParameters(
            List.of(entity(anonymousMethod())), rules("""
                { "omit": ["parameter names"] }
                """)));
    }

    @Test
    void noParameterAnywhere_saysNothing() {
        assertFalse(ProjectScanner.reportAnonymousParameters(
            List.of(entity(new UmlMethod("list", "int", "+", false, List.of()))),
            Rules.no()));
    }

    @Test
    void aNeighbourhoodReducedToItsCentre_isReported() {
        UmlEntity only = UmlEntity.builder("com.example.Only", "Only").build();
        Set<String> wanted = new LinkedHashSet<>(List.of(only.qualifiedName()));

        assertTrue(ViewBuilder.reportEmptyNeighborhood(wanted, ImmutableList.of(only),
            "Only", "Around of Only"));
    }

    @Test
    void aNeighbourhoodThatFound_saysNothing() {
        UmlEntity center = UmlEntity.builder("com.example.Center", "Center").build();
        Set<String> wanted =
            new LinkedHashSet<>(List.of(center.qualifiedName(), "com.example.Neighbour"));

        assertFalse(ViewBuilder.reportEmptyNeighborhood(wanted, ImmutableList.of(center),
            "Center", "Around of Center"));
    }

    @Test
    void aMisspelledTypeRule_isReported() {
        Rules read = rules("""
            { "types": { "ImutableList": "List" } }
            """);
        read.nameDisplayed(ImmutableList.class);

        assertEquals(List.of("types: ImutableList -> List"), read.rulesWithoutEffect());
    }

    @Test
    void aRuleThatServed_isNotReported() {
        Rules read = rules("""
            { "types": { "ImmutableList": "List" }, "hide": ["log"] }
            """);
        read.nameDisplayed(ImmutableList.class);
        assertTrue(read.hiddenMember("log"));

        assertTrue(read.rulesWithoutEffect().isEmpty(), read.rulesWithoutEffect().toString());
    }

    @Test
    void deadPatterns_areAllReported() {
        Rules read = rules("""
            { "types": { "ImutableList": "List" }, "hide": ["get*", "LOGGR"] }
            """);
        read.hiddenMember("compute");
        read.hiddenMember("log");

        assertEquals(List.of("types: ImutableList -> List", "hide: get*", "hide: LOGGR"),
            read.rulesWithoutEffect());
    }

    @Test
    void reportingDeadRules_arisesOnlyIfAnEntryIsDead() {
        assertTrue(ProjectScanner.reportRulesWithoutEffect(rules("""
            { "hide": ["LOGGR"] }
            """)));
        assertFalse(ProjectScanner.reportRulesWithoutEffect(rules("{}")));
    }

    @Test
    void anUnreadableClass_isReported() {
        assertTrue(ProjectScanner.reportClassesUnreadable(
            ImmutableList.of("com.example.Missing (java.lang.NoClassDefFoundError: com/ex/Parti)")));
    }

    @Test
    void noUnreadableClass_saysNothing() {
        assertFalse(ProjectScanner.reportClassesUnreadable(ImmutableList.of()));
    }
}
