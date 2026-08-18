package io.github.oliviercailloux.umlg.extraction.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.rendering.PlantUmlAssert;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RulesTest {

    private static Rules rules(String json) {
        return Rules.read(json, "umlg.json");
    }

    @Test
    void simpleName_isReplaced() {
        assertEquals("List", rules("""
            { "types": { "ImmutableList": "List" } }
            """).nameDisplayed(ImmutableList.class));
    }

    @Test
    void qualifiedName_isReplaced() {
        assertEquals("List", rules("""
            { "types": { "com.google.common.collect.ImmutableList": "List" } }
            """).nameDisplayed(ImmutableList.class));
    }

    @Test
    void qualifiedName_winsOverTheSimpleName() {
        Rules rules = rules("""
            {
              "types": {
                "ImmutableList": "Collection",
                "com.google.common.collect.ImmutableList": "List"
              }
            }
            """);

        assertEquals("List", rules.nameDisplayed(ImmutableList.class));
    }

    @Test
    void typeWithoutRule_keepsItsSimpleName() {
        assertEquals("String", rules("""
            { "types": { "ImmutableList": "List" } }
            """).nameDisplayed(String.class));
    }

    @Test
    void underscoredKeys_areIgnored() {
        Rules rules = rules("""
            {
              "_": "Guava's types teach the reader nothing.",
              "_why": "And a second note does not get in the way of the first.",
              "types": { "ImmutableSet": "Set" }
            }
            """);

        assertEquals("Set", rules.nameDisplayed(ImmutableSet.class));
    }

    @Test
    void emptyFile_givesNoRule() {
        assertTrue(rules("").empty());
        assertTrue(rules("   \n").empty());
        assertTrue(rules("{}").empty());
        assertTrue(Rules.no().empty());
    }

    @Test
    void unknownRule_isRefusedRecallingTheKnownOnes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> rules("""
            {
              "types": { "ImmutableList": "List" },
              "color": { "Client": "red" }
            }
            """));

        assertTrue(e.getMessage().contains("umlg.json"), e.getMessage());
        assertTrue(e.getMessage().contains("color"), e.getMessage());
        assertTrue(e.getMessage().contains("types"), e.getMessage());
        assertTrue(e.getMessage().contains("views"), e.getMessage());
    }

    @Test
    void faultySyntax_isRefusedNamingTheLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> rules("""
            {
              "strategy": "public",
            }
            """));

        assertTrue(e.getMessage().contains("umlg.json"), e.getMessage());
        assertTrue(e.getMessage().contains("line 3"), e.getMessage());
    }

    @Test
    void oldFormatComment_isRefusedSayingWhereANoteGoes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> rules("""
            # Guava's types teach the reader nothing.
            { "types": { "ImmutableSet": "Set" } }
            """));

        assertTrue(e.getMessage().contains("JSON"), e.getMessage());
        assertTrue(e.getMessage().contains("_"), e.getMessage());
    }

    @Test
    void aTopLevelObjectIsExpected_andNothingElse() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            [ { "strategy": "public" } ]
            """));

        assertTrue(refusal.getMessage().contains("expected a JSON object"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("an array"), refusal.getMessage());
    }

    @Test
    void whatFollowsTheClosingBrace_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> rules("""
            { "strategy": "public" }
            { "order": "declaration" }
            """));
    }

    @Test
    void valueOfTheWrongKind_isRefusedNamingTheKey() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": "get*" }
            """));

        assertTrue(refusal.getMessage().contains("hide"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("array"), refusal.getMessage());
    }

    @Test
    void displayedTypeThatIsNotAString_isRefused() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "types": { "ImmutableList": 3 } }
            """));

        assertTrue(refusal.getMessage().contains("types.ImmutableList"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("a number"), refusal.getMessage());
    }

    @Test
    void missingHalf_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> rules("""
            { "types": { "": "List" } }
            """));
        assertThrows(IllegalArgumentException.class, () -> rules("""
            { "types": { "ImmutableList": "" } }
            """));
    }

    @Test
    void twoRulesOnTheSameType_areRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> rules("""
            {
              "types": {
                "ImmutableList": "List",
                "ImmutableList": "Collection"
              }
            }
            """));

        assertTrue(e.getMessage().contains("umlg.json"), e.getMessage());
        assertTrue(e.getMessage().contains("ImmutableList"), e.getMessage());
    }

    @Test
    void omitReturnVoid_erasesTheReturnedType() {
        assertEquals("", rules("""
            { "omit": ["return void"] }
            """).nameDisplayedReturn(void.class));
    }

    @Test
    void omitReturnVoid_touchesTheReturnOnly() {
        Rules rules = rules("""
            { "omit": ["return void"] }
            """);

        assertEquals("void", rules.nameDisplayed(void.class));
    }

    @Test
    void omitReturnVoid_touchesNoOtherType() {
        assertEquals("String", rules("""
            { "omit": ["return void"] }
            """).nameDisplayedReturn(String.class));
    }

    @Test
    void withoutOmitReturnVoid_theVoidStaysWritten() {
        assertEquals("void", Rules.no().nameDisplayedReturn(void.class));
    }

    @Test
    void omitReturnVoid_combinesWithAnAlias() {
        Rules rules = rules("""
            {
              "omit": ["return void"],
              "types": { "ImmutableList": "List" }
            }
            """);

        assertEquals("", rules.nameDisplayedReturn(void.class));
        assertEquals("List", rules.nameDisplayedReturn(ImmutableList.class));
    }

    @Test
    void omitWithUnknownValue_isRefusedRecallingTheFourForms() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "omit": ["types"] }
            """));

        assertTrue(e.getMessage().contains("visibility"), e.getMessage());
        assertTrue(e.getMessage().contains("parameter names"), e.getMessage());
        assertTrue(e.getMessage().contains("return void"), e.getMessage());
        assertTrue(e.getMessage().contains("abstract"), e.getMessage());
        assertTrue(e.getMessage().contains("omit[0]"), e.getMessage());
    }

    @Test
    void omitAbstract_isRead() {
        assertTrue(rules("""
            { "omit": ["abstract"] }
            """).abstractOmit());
    }

    @Test
    void withoutOmitAbstract_theMarkStaysAskedFor() {
        assertFalse(Rules.no().abstractOmit());
        assertFalse(rules("""
            { "omit": ["visibility"] }
            """).abstractOmit());
    }

    @Test
    void omitAbstractTwice_isRefused() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "omit": ["abstract", "abstract"] }
            """));

        assertTrue(refusal.getMessage().contains("already asked for"), refusal.getMessage());
    }

    @Test
    void omitAbstract_isNotEnoughToMakeTheRulesEmpty() {
        assertFalse(rules("""
            { "omit": ["abstract"] }
            """).empty());
    }

    @Test
    void theSameOmitTwice_isRefused() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "omit": ["return void", "return void"] }
            """));

        assertTrue(refusal.getMessage().contains("already asked for"), refusal.getMessage());
    }

    @Test
    void hideReturn_noLongerExists() {
        assertThrows(IllegalArgumentException.class, () -> rules("""
            { "omit": ["return String"] }
            """));
    }

    @Test
    void missingFile_givesAnEmptySet(@TempDir Path tmp) throws IOException {
        assertTrue(Rules.project(tmp).empty(),
            "The vast majority of projects have no rules file.");
    }

    @Test
    void presentFile_isReadAtTheRootOfTheAnalysedDirectory(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve(Rules.FILE_NAME),
            "{ \"types\": { \"ImmutableList\": \"List\" } }\n", StandardCharsets.UTF_8);

        assertEquals("List", Rules.project(tmp).nameDisplayed(ImmutableList.class));
    }

    @Test
    void faultyFile_stopsGeneration(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve(Rules.FILE_NAME), "{ \"types\": }\n",
            StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> Rules.project(tmp));
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Keyboard {
        private ImmutableList<String> keys;

        public ImmutableSet<String> names(ImmutableList<String> filter) {
            return ImmutableSet.of();
        }
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Switch {
        public void toggle() {}

        public String state() {
            return "";
        }
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    interface Toggle {
        String state();

        default boolean on() {
            return false;
        }

        static Toggle create() {
            return null;
        }
    }

    @Test
    void withoutOmitAbstract_theAbstractOperationCarriesItsMark() {
        String source =
            PlantUmlAssert.documentFor(EntityExtractor.extract(Toggle.class, Rules.no()));

        assertTrue(source.contains("{abstract} state()"),
            "Italics are UML's notation for an operation without implementation:\n"
                + source);
    }

    @Test
    void omitAbstract_erasesTheMarkAndLeavesTheRest() {
        String source = PlantUmlAssert.documentFor(
            EntityExtractor.extract(Toggle.class, rules("""
                { "omit": ["abstract"] }
                """)));

        assertFalse(source.contains("{abstract}"), "The mark must vanish:\n" + source);

        assertTrue(source.contains("+ state() : String"), source);
        assertTrue(source.contains("+ on() : boolean"), source);
        assertTrue(source.contains("{static} create()"), source);
    }

    @Test
    void omitAbstract_doesNotTouchTheBoxKeyword() {
        String source = PlantUmlAssert.documentFor(
            EntityExtractor.extract(Toggle.class, rules("""
                { "omit": ["abstract"] }
                """)));

        assertTrue(source.contains("interface "), source);
    }

    @Test
    void omitReturnVoid_erasesTheBoxVoidAndOnlyThat() {
        String source = PlantUmlAssert.documentFor(EntityExtractor.extract(Switch.class,
            rules("""
                { "omit": ["return void"] }
                """)));

        assertTrue(source.contains("+ toggle()\n"),
            "The void must leave neither type nor colon:\n" + source);
        assertTrue(source.contains("+ state() : String"), source);
        PlantUmlAssert.assertCompile(source);
    }

    @Test
    void withoutRule_theVoidIsWritten() {
        String source = PlantUmlAssert.documentFor(EntityExtractor.extract(Switch.class));

        assertTrue(source.contains("+ toggle() : void"), source);
    }

    @Test
    void rule_appliesToFieldsReturnsAndParameters() {
        Rules rules = rules("""
            { "types": { "ImmutableList": "List", "ImmutableSet": "Set" } }
            """);

        List<UmlField> fields =
            MemberExtractor.extractFields(Keyboard.class, UMLVisibility.PRIVATE, rules);
        var method = MemberExtractor.extractMethods(Keyboard.class, UMLVisibility.PRIVATE, rules)
            .stream().filter(m -> m.name().equals("names")).findFirst().orElseThrow();

        assertEquals("List<String>", fields.get(0).type(), "L'attribute : " + fields);
        assertEquals("Set<String>", method.returnType());
        assertEquals("List<String>", method.parameters().get(0).type());
    }

    @Test
    void withoutRule_theCodeDescribesItselfAsWritten() {
        List<UmlField> fields =
            MemberExtractor.extractFields(Keyboard.class, UMLVisibility.PRIVATE, Rules.no());

        assertEquals("ImmutableList<String>", fields.get(0).type());
    }

    @Test
    void writtenStrategy_differsFromAMissingKey() {
        assertTrue(rules("""
            { "strategy": "none" }
            """).strategyDeclared());
        assertFalse(rules("{}").strategyDeclared());
    }

    @Test
    void declarationOrder_isAskedFor() {
        assertTrue(rules("""
            { "order": "declaration" }
            """).orderDeclaration());
        assertFalse(rules("""
            { "order": "alphabetical" }
            """).orderDeclaration());
        assertFalse(rules("{}").orderDeclaration());
    }

    @Test
    void unknownOrder_isRefusedNamingBoth() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "order": "source" }
            """));

        assertTrue(refusal.getMessage().contains("declaration"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("alphabetical"), refusal.getMessage());
    }

    @Test
    void twoOrderKeys_areRefused() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            {
              "order": "declaration",
              "order": "alphabetical"
            }
            """));

        assertTrue(refusal.getMessage().contains("umlg.json"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("order"), refusal.getMessage());
    }

    @Test
    void parameterNames_fallSilent() {
        assertTrue(rules("""
            { "omit": ["parameter names"] }
            """).parametersOmitNames());
        assertFalse(rules("{}").parametersOmitNames());
    }

    @Test
    void aPattern_aimsAtBothKindsOfMembers() {
        Rules read = rules("""
            { "hide": ["log", "get*"] }
            """);

        assertTrue(read.hiddenMember("log"));
        assertTrue(read.hiddenMember("getName"));
        assertFalse(read.hiddenMember("compute"));
    }

    @Test
    void twoPatternsInTheSameString_areRefused() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["method get*"] }
            """));

        assertTrue(refusal.getMessage().contains("cannot contain a space"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("hide[0]"), refusal.getMessage());
    }

    @Test
    void qualifiedPattern_isRefusedPointingToUmlExclude() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["Representation.string"] }
            """));

        assertTrue(refusal.getMessage().contains("@UMLExclude"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("Representation.string"), refusal.getMessage());
    }

    @Test
    void ordinaryPattern_staysAccepted() {
        assertTrue(rules("""
            { "hide": ["s*"] }
            """).hiddenMember("string"));
    }

    @Test
    void aFileThatIsNotUtf8_isRefusedSayingSo(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("umlg.json");
        Files.write(file, ("{ \"_\": \"the café rules\", \"strategy\": \"public\" }\n")
            .getBytes(StandardCharsets.ISO_8859_1));

        var refusal = assertThrows(IllegalArgumentException.class, () -> Rules.file(file));

        assertTrue(refusal.getMessage().contains("UTF-8"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(file.toString()), refusal.getMessage());
    }

    @Test
    void qualifiedPatternWithStar_isRefusedToo() {
        assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["Representation.*"] }
            """));
    }

    @Test
    void aLeadingBom_isNamedAndNotTakenForAStrayNote() {
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> rules((char) 0xFEFF + "{ \"strategy\": \"public\" }"));

        assertTrue(refusal.getMessage().contains("byte order mark"), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("\"_\""), refusal.getMessage());
    }

    @Test
    void patternMadeOfStarsOnly_isRefusedLikeTheLoneStar() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["**"] }
            """));

        assertTrue(refusal.getMessage().contains("would hide every member of every class"),
            refusal.getMessage());
        assertTrue(refusal.getMessage().contains("\"**\""), refusal.getMessage());
    }

    @Test
    void aLoneStar_staysRefused() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["*"] }
            """));

        assertTrue(refusal.getMessage().contains("would hide every member of every class"),
            refusal.getMessage());
    }

    @Test
    void patternNotTakingEverything_staysAccepted() {
        Rules rules = rules("""
            { "hide": ["*e*"] }
            """);

        assertTrue(rules.hiddenMember("named"));
        assertFalse(rules.hiddenMember("total"));
    }

    @Test
    void patternWithATab_isRefused() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["get\\tNom"] }
            """));

        assertTrue(refusal.getMessage().contains("cannot contain a space"), refusal.getMessage());
    }

    @Test
    void patternWithANonBreakingSpace_isRefused() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["get\\u00a0Name"] }
            """));

        assertTrue(refusal.getMessage().contains("cannot contain a space"), refusal.getMessage());
    }

    @Test
    void typeWithANonBreakingSpace_isRefused() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "types": { "Immutable\\u00a0List": "List" } }
            """));

        assertTrue(refusal.getMessage().contains("cannot contain a space"), refusal.getMessage());
    }

    @Test
    void theSamePatternTwice_isRefused() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["log", "get*", "log"] }
            """));

        assertTrue(refusal.getMessage().contains("already asked for"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("hide[2]"), refusal.getMessage());
    }

    @Test
    void twoDistinctPatterns_stayAccepted() {
        Rules rules = rules("""
            { "hide": ["log", "get*"] }
            """);

        assertTrue(rules.hiddenMember("log"));
        assertTrue(rules.hiddenMember("getName"));
    }

    @Test
    void noteInTypes_isNeitherARuleNorAFault() {
        Rules rules = rules("""
            {
              "types": {
                "_note": "Guava's types teach the reader nothing",
                "ImmutableList": "List"
              }
            }
            """);

        assertEquals("List", rules.nameDisplayed(ImmutableList.class));
        assertEquals(List.of(), rules.rulesWithoutEffect(),
            "A note is not a rule, hence never a dead rule.");
    }

    @Test
    void schema_isNeitherARuleNorAFault() {
        Rules rules = rules("""
            {
              "$schema": "./umlg-schema.json",
              "types": { "ImmutableList": "List" }
            }
            """);

        assertEquals("List", rules.nameDisplayed(ImmutableList.class));
    }

    @Test
    void schemaAlone_doesNotMakeARule() {
        assertTrue(rules("""
            { "$schema": "./umlg-schema.json" }
            """).empty());
    }

    @Test
    void schemaInAView_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> rules("""
            {
              "views": [
                { "$schema": "./umlg-schema.json", "name": "Billing", "retain": ["Invoice"] }
              ]
            }
            """));
    }

    @Test
    void noteInAView_isNeitherAKeyNorAFault() {
        assertEquals(1, rules("""
            {
              "views": [
                { "_": "why this view", "name": "Billing", "retain": ["Invoice"] }
              ]
            }
            """).views().size());
    }

    @Test
    void uppercaseStrategy_isRefusedLikeTheOtherWords() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "strategy": "PUBLIC" }
            """));

        assertTrue(refusal.getMessage().contains("unknown strategy"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("lower case"), refusal.getMessage());
    }

    @Test
    void lowercaseStrategy_staysRead() {
        assertEquals(UMLVisibility.PROTECTED, rules("""
            { "strategy": "protected" }
            """).strategy());
    }

    @Test
    void projectStrategy_isRefusedSayingWhy() {
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "strategy": "project" }
            """));

        assertTrue(refusal.getMessage().contains("defer to this key"), refusal.getMessage());
    }

    @Test
    void aWrittenDefault_doesNotMakeTheRulesEmpty() {
        assertFalse(rules("""
            { "showEmptyMembers": false }
            """).empty());
        assertFalse(rules("""
            { "order": "alphabetical" }
            """).empty());
        assertFalse(rules("""
            { "viewsByPackage": false }
            """).empty());
        assertFalse(rules("""
            { "direction": "top-to-bottom" }
            """).empty());
    }

    @Test
    void notesAlone_leaveTheRulesEmpty() {
        assertTrue(rules("""
            { "_": "nothing to ask for now", "_more": "really nothing" }
            """).empty());
    }
}
