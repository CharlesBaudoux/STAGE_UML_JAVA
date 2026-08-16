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

// The three settings the rules file brings to the whole project: the default
// the default, the noise filter, and the filing of a package in a rectangle.
//
// What they have in common is what warrants testing them together: each replaces a
// annotation that would otherwise have to be repeated on every class, and each yields to
// the annotation when it is written. It is this second half — the rule loses against the
// special case — which counts most, and which is checked every time.
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

    // --- The project strategy ---

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
        // The historical behaviour, preserved: a project without a rules file behaves
        // exactly as before the setting existed.
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
        // The case alone warranting the existence of UMLVisibility.PROJECT: without it, "the
        // the class said nothing" and "the class asked for NONE" would be indistinguishable, and this
        // box would fill up against the will of its author.
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
        // "project" is what a class writes to refer to this key: the key cannot
        // point to itself.
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

    // --- The noise filter ---

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
        // The deep change: the library no longer decides what deserves to be drawn.
        // A project saying nothing sees everything its strategy allows, accessors included.
        List<String> methods = methodsNames(EntityExtractor.extract(Verbose.class));

        assertTrue(methods.contains("getName"), methods.toString());
        assertTrue(methods.contains("toString"), methods.toString());
    }

    @Test
    void accessorPattern_hidesGettersAndSetters() {
        // Accessors are designated like everything else, by their names: the file has no
        // a key reserved for them. Each pattern is independent — "get*" says nothing about "set*".
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
        // With no star, the pattern holds for the exact name only: hide "toString" does not touch
        // not at the accessors.
        List<String> methods = methodsNames(EntityExtractor.extract(Verbose.class, rules("""
            { "hide": ["toString"] }
            """)));

        assertFalse(methods.contains("toString"), methods.toString());
        assertTrue(methods.contains("getName"), methods.toString());
    }

    @Test
    void methodPattern_hidesTheProjectConventions() {
        // "withName" follows no convention the library would know: it is
        // exactly what the patterns are there to cover — the project's own conventions.
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
        // The most glaring gap of the old filter: it looked at methods only, so that
        // than a logger could be hidden only with an @UMLExclude written by hand on each class. The
        // the pattern is the same as for a method: the file does not tell the two kinds apart.
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
        // The precedence rule: the special case written on the member beats the general setting.
        UmlEntity entity = EntityExtractor.extract(RecoveredByHand.class, rules("""
            { "hide": ["get*", "log*"] }
            """));

        assertTrue(fieldsNames(entity).contains("logger"), entity.fields().toString());
        assertTrue(methodsNames(entity).contains("getName"), entity.methods().toString());
    }

    @Test
    void patternAimingAtNothing_isReportedAtTheEndOfTheScan() {
        // "hide" takes patterns only, and every member name is one: nothing tells
        // a typo from an intention, and refusal is therefore impossible. It is the report
        // dead rules takes over — the same net as for "LOGGR" or the lone
        // forgotten from a "get".
        Rules read = rules("""
            { "hide": ["accessors"] }
            """);

        assertEquals(List.of("hide: accessors"), read.rulesWithoutEffect());
    }

    @Test
    void hideWithoutPattern_hidesNothing() {
        // The empty array asks for nothing, and can no longer be the typo that an
        // "hide" without a pattern: it is now the parser that refuses a badly written list.
        Rules read = rules("""
            { "hide": [] }
            """);

        assertFalse(read.hiddenMember("getName"));
        assertTrue(read.rulesWithoutEffect().isEmpty(), read.rulesWithoutEffect().toString());
    }

    @Test
    void patternOnItsOwn_isRefused() {
        // "*" would hide the whole compartment: valid in theory, unexpected enough that
        // refuses to guess it.
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

    // --- The visibility symbols ---

    @Test
    void byDefault_theVisibilitiesAreWritten() {
        // UML asks for them, and they say something no other mark says.
        assertEquals("-", EntityExtractor.extract(Verbose.class).fields().stream()
            .filter(f -> f.name().equals("name")).findFirst().orElseThrow().visibility());
    }

    @Test
    void omitVisibility_silencesThem() {
        // Many hand-kept diagrams omit them, because they show a
        // a structure rather than an API. The project must be able to do the same.
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
        // The symbol vanishes, and the space that followed it with it: otherwise each line
        // would start with a blank nothing justifies.
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

    // --- Filing a package: @UMLPackage, and it alone ---

    @UMLClass
    static class Filed {}

    @UMLClass
    @UMLPackage(name = "Written by hand")
    static class AlreadyFiled {}

    private static final String Package = ProjectRulesTest.class.getPackageName();

    @Test
    void groupPackage_noLongerExists() {
        // A package is a name, and a rectangle set over named classes is a
        // a special case, not a project setting: it is @UMLPackage that says it, on the element.
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

    // --- The file as a whole ---

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
