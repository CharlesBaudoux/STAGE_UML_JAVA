package io.github.oliviercailloux.umlg.extraction.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectRulesTest {

    private static Rules rules(String json) {
        return Rules.read(json, "the rules of the test");
    }

    private static List<String> methodsNames(UmlEntity entity) {
        return entity.methods().stream().map(UmlMethod::name).toList();
    }

    private static List<String> fieldsNames(UmlEntity entity) {
        return entity.fields().stream().map(UmlField::name).toList();
    }

    @UMLClass
    static class WithoutStrategy {
        public String name;

        public void validate() {}
    }

    @UMLClass(strategy = UMLVisibility.NONE)
    static class EmptyBoxWanted {
        public String name;

        public void validate() {}
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class AllWanted {
        private String secret;
    }

    @Test
    void withoutFile_aClassSayingNothingStaysEmpty() {
        UmlEntity entity = EntityExtractor.extract(WithoutStrategy.class);

        assertTrue(entity.fields().isEmpty(), entity.fields().toString());
        assertTrue(entity.methods().isEmpty(), entity.methods().toString());
    }

    @Test
    void projectStrategy_appliesToClassesSayingNothing() {
        UmlEntity entity = EntityExtractor.extract(WithoutStrategy.class, rules("""
            { "strategy": "public" }
            """));

        assertEquals(List.of("name"), fieldsNames(entity));
        assertEquals(List.of("validate"), methodsNames(entity));
    }

    @Test
    void classAskingForNone_staysEmptyDespiteTheProject() {
        UmlEntity entity = EntityExtractor.extract(EmptyBoxWanted.class, rules("""
            { "strategy": "public" }
            """));

        assertTrue(entity.fields().isEmpty(), entity.fields().toString());
        assertTrue(entity.methods().isEmpty(), entity.methods().toString());
    }

    @Test
    void classDeclaringItsStrategy_winsOverTheProject() {
        UmlEntity entity = EntityExtractor.extract(AllWanted.class, rules("""
            { "strategy": "public" }
            """));

        assertEquals(List.of("secret"), fieldsNames(entity),
            "PRIVATE on the class must win over \"\\\"strategy\\\": \\\"public\\\"\".");
    }

    @Test
    void unknownStrategy_isRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "strategy": "publik" }
            """));

        assertTrue(e.getMessage().contains("unknown strategy"), e.getMessage());
    }

    @Test
    void projectStrategy_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> rules("""
            { "strategy": "project" }
            """));
    }

    @Test
    void twoStrategies_areRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> rules("""
            {
              "strategy": "none",
              "strategy": "public"
            }
            """));

        assertTrue(e.getMessage().contains("strategy"), e.getMessage());
        assertTrue(e.getMessage().contains("the rules of the test"), e.getMessage());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Verbose {
        private String logger;
        private String name;

        public String getName() { return name; }

        public void setName(String v) { name = v; }

        public Verbose withName(String v) { return this; }

        public void validate() {}

        @Override
        public String toString() { return ""; }
    }

    @Test
    void withoutRule_nothingIsHiddenAnyMore() {
        List<String> methods = methodsNames(EntityExtractor.extract(Verbose.class));

        assertTrue(methods.contains("getName"), methods.toString());
        assertTrue(methods.contains("toString"), methods.toString());
    }

    @Test
    void accessorPattern_hidesGettersAndSetters() {
        List<String> methods = methodsNames(EntityExtractor.extract(Verbose.class, rules("""
            { "hide": ["get*", "set*"] }
            """)));

        assertFalse(methods.contains("getName"), methods.toString());
        assertFalse(methods.contains("setName"), methods.toString());
        assertTrue(methods.contains("validate"), "The business method stays.");
        assertTrue(methods.contains("toString"),
            "What no pattern aims at stays displayed.");
    }

    @Test
    void exactPattern_hidesTheWrittenNameOnly() {
        List<String> methods = methodsNames(EntityExtractor.extract(Verbose.class, rules("""
            { "hide": ["toString"] }
            """)));

        assertFalse(methods.contains("toString"), methods.toString());
        assertTrue(methods.contains("getName"), methods.toString());
    }

    @Test
    void methodPattern_hidesTheProjectConventions() {
        List<String> methods = methodsNames(EntityExtractor.extract(Verbose.class, rules("""
            { "hide": ["with*"] }
            """)));

        assertFalse(methods.contains("withName"), methods.toString());
        assertTrue(methods.contains("validate"), methods.toString());
    }

    @Test
    void severalPatterns_areWrittenInTheSameArray() {
        List<String> methods = methodsNames(EntityExtractor.extract(Verbose.class, rules("""
            { "hide": ["with*", "validate"] }
            """)));

        assertFalse(methods.contains("withName"), methods.toString());
        assertFalse(methods.contains("validate"), methods.toString());
    }

    @Test
    void fieldPattern_hidesAField() {
        List<String> fields = fieldsNames(EntityExtractor.extract(Verbose.class, rules("""
            { "hide": ["log*"] }
            """)));

        assertFalse(fields.contains("logger"), fields.toString());
        assertTrue(fields.contains("name"), fields.toString());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class RecoveredByHand {
        @UMLInclude
        private String logger;

        @UMLInclude
        public String getName() { return ""; }
    }

    @Test
    void includeWinsOverTheRule() {
        UmlEntity entity = EntityExtractor.extract(RecoveredByHand.class, rules("""
            { "hide": ["get*", "log*"] }
            """));

        assertTrue(fieldsNames(entity).contains("logger"), entity.fields().toString());
        assertTrue(methodsNames(entity).contains("getName"), entity.methods().toString());
    }

    @Test
    void patternAimingAtNothing_isReportedAtTheEndOfTheScan() {
        Rules read = rules("""
            { "hide": ["accessors"] }
            """);

        assertEquals(List.of("hide: accessors"), read.rulesWithoutEffect());
    }

    @Test
    void hideWithoutPattern_hidesNothing() {
        Rules read = rules("""
            { "hide": [] }
            """);

        assertFalse(read.hiddenMember("getName"));
        assertTrue(read.rulesWithoutEffect().isEmpty(), read.rulesWithoutEffect().toString());
    }

    @Test
    void patternOnItsOwn_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["*"] }
            """));
    }

    @Test
    void emptyPattern_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["get*", ""] }
            """));
    }

    @Test
    void byDefault_theVisibilitiesAreWritten() {
        assertEquals("-", EntityExtractor.extract(Verbose.class).fields().stream()
            .filter(f -> f.name().equals("name")).findFirst().orElseThrow().visibility());
    }

    @Test
    void omitVisibility_silencesThem() {
        UmlEntity entity = EntityExtractor.extract(Verbose.class, rules("""
            { "omit": ["visibility"] }
            """));

        assertTrue(entity.fields().stream().allMatch(f -> f.visibility().isEmpty()),
            entity.fields().toString());
        assertTrue(entity.methods().stream().allMatch(m -> m.visibility().isEmpty()),
            entity.methods().toString());
    }

    @Test
    void omitVisibility_leavesNoSpaceInTheDrawing() {
        String source = io.github.oliviercailloux.umlg.rendering.PlantUmlAssert.documentFor(
            EntityExtractor.extract(Verbose.class, rules("""
                { "omit": ["visibility"] }
                """)));

        assertTrue(source.contains("  name : String"), source);
        assertFalse(source.contains("   name"), "No surplus space: " + source);
    }

    @Test
    void omitVisibility_twice_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> rules("""
            { "omit": ["visibility", "visibility"] }
            """));
    }

    @UMLClass
    static class Filed {}

    @UMLClass
    @UMLPackage(name = "Written by hand")
    static class AlreadyFiled {}

    private static final String Package = ProjectRulesTest.class.getPackageName();

    @Test
    void groupPackage_noLongerExists() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> rules("{ \"group\": { \"" + Package + "\": \"Rules\" } }"));

        assertTrue(e.getMessage().contains("unknown rule"), e.getMessage());
    }

    @Test
    void withoutAnnotation_noRuleFilesAClass() {
        UmlEntity entity = EntityExtractor.extract(Filed.class, rules("""
            { "strategy": "public" }
            """));

        assertFalse(entity.group().isGrouped(), entity.group().toString());
    }

    @Test
    void theAnnotationFilesTheClass() {
        UmlEntity entity = EntityExtractor.extract(AlreadyFiled.class, rules("""
            { "strategy": "public" }
            """));

        assertEquals("Written by hand", entity.group().name());
    }

    @Test
    void completeFile_isReadWithoutError() {
        Rules read = rules("""
            {
              "_": "The settings of the whole project",
              "strategy": "public",

              "hide": ["get*", "set*", "with*", "visit*", "log", "LOGGER"],
              "omit": ["return void", "visibility"],
              "types": { "ImmutableList": "List" }
            }
            """);

        assertFalse(read.empty());
        assertEquals(UMLVisibility.PUBLIC, read.strategy());
        assertTrue(read.hiddenMember("getName"));
        assertTrue(read.hiddenMember("withName"));
        assertTrue(read.hiddenMember("LOGGER"));
        assertTrue(read.omitVisibility());
        assertEquals("", read.nameDisplayedReturn(void.class));
        assertEquals("List", read.nameDisplayed(com.google.common.collect.ImmutableList.class));
    }

    @Test
    void emptyFile_setsNothing() {
        assertTrue(rules("{}").empty());
        assertEquals(UMLVisibility.NONE, rules("{}").strategy());
    }
}
