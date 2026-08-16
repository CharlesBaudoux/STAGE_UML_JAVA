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

// The end-of-scan warnings: which surprises are reported, and which are not
// not.
//
// Restraint counts as much as reporting. Each of these warnings addresses a
// a user whose diagram is right but says less than they believe they asked for: a
// a box empty for lack of strategy, silent signatures for lack of -parameters, a neighbourhood with no
// neighbour. A warning firing on a deliberate choice — "strategy none" written,
// names hidden by a rule — would teach one to stop reading them.
class GenerationWarningsTest {

    private static Rules rules(String json) {
        return Rules.read(json, "umlg.json");
    }

    // --- Boxes empty for lack of a strategy ---

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
        // The typical first attempt: everything is annotated, nothing is declared, the boxes come out
        // empty shells and nothing said why.
        assertTrue(emptyBox(Silent.class, Rules.no()));
    }

    @Test
    void writtenStrategyNone_silencesTheReport() {
        // The same empty box, but chosen: a written line is a decision, not an oversight.
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
        // A marker interface would stay empty under any strategy: reporting it would blame
        // the wrong cause.
        assertFalse(emptyBox(Marker.class, Rules.no()));
    }

    @Test
    void theReport_namesTheBoxes() {
        assertTrue(ProjectScanner.reportEmptyBoxes(ImmutableList.of("Silent")));
        assertFalse(ProjectScanner.reportEmptyBoxes(ImmutableList.of()));
    }

    // --- Silent signatures, the mark of a compilation without -parameters ---

    private static UmlEntity entity(UmlMethod... methods) {
        return UmlEntity.builder("com.example.Item", "Item")
            .methods(List.of(methods)).build();
    }

    private static UmlMethod anonymousMethod() {
        // The convenience constructor builds parameters without names, exactly what
        // reflection on a project compiled without the option.
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
        // A project compiled with -parameters has every name; seeing a single one proves that
        // the option is there, and that any absences have another cause.
        assertFalse(ProjectScanner.reportAnonymousParameters(
            List.of(entity(anonymousMethod()), entity(namedMethod())), Rules.no()));
    }

    @Test
    void namesHiddenByTheRule_areNotReported() {
        // "omit parameter names" silenced them deliberately: nothing to explain.
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

    // --- Le neighborhood without neighbor ---

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

    // --- The rules left a dead letter ---

    @Test
    void aMisspelledTypeRule_isReported() {
        // Yesterday's perfect no-op: the rule never applied, the diagram was drawn,
        // and nothing showed the dead line. The report names it as it was written.
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
        // The two natures of naming rules, each aiming at a name nothing carries.
        Rules read = rules("""
            { "types": { "ImutableList": "List" }, "hide": ["get*", "LOGGR"] }
            """);
        read.hiddenMember("compute");
        read.hiddenMember("log");

        // The order of the report follows that of the rule natures: the types, then the patterns.
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

    // --- The classes the loader could not read ---
    //
    // They no longer fail the scan: a "provided" dependency missing from the classpath
    // passed on is enough to make a .class unreadable, and it would be absurd that a class whose
    // the diagram does not want prevents drawing it. But silencing them would be worse — one cannot
    // know whether they carried @UMLClass, since the annotation is read only on a class
    // loaded. The diagram is therefore perhaps incomplete, and that is said.

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
