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

// The rules file: what it accepts, what it refuses, and what it changes in the diagram.
//
// Two requirements answer each other. A hand-written file has no compiler to read it back:
// every refusal must therefore name the faulty spot and recall the expected form. And a rule
// accepted must show on the drawing, otherwise the user would believe it was applied.
class RulesTest {

    private static Rules rules(String json) {
        return Rules.read(json, "umlg.json");
    }

    // --- What the file says ---

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
        // A rule precisely designating a class must take precedence over the one aiming at all its
        // homonyms: otherwise the requested precision would have no effect.
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
        // JSON has no comments, and a hand-written file needs to say why.
        // A key starting with "_" is not a rule: that is where the note goes.
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
        // A file created but not yet written is not a fault: it gives the same rules
        // than a missing file, like the empty object one would write in it.
        assertTrue(rules("").empty());
        assertTrue(rules("   \n").empty());
        assertTrue(rules("{}").empty());
        assertTrue(Rules.no().empty());
    }

    // --- What the file refuses ---

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
        // The message must say what is known, otherwise the author has nothing to correct.
        assertTrue(e.getMessage().contains("types"), e.getMessage());
        assertTrue(e.getMessage().contains("views"), e.getMessage());
    }

    @Test
    void faultySyntax_isRefusedNamingTheLine() {
        // A syntax fault is the only one the parser locates better than we do: it knows the
        // line, where the JSON path does not exist yet.
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
        // The file was written line by line, and the notes opened there with "#". The one who
        // carries its old file over to JSON must read where its note can go.
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
        // A brace closed too early leaves the rest of the file without effect, and the diagram
        // is then not the one asked for.
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
        // The second would win silently, and the author would not know which one applies.
        // It is the parser that reports it here, and for every key at once.
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

    // --- "return void": the return type that is silenced ---

    @Test
    void omitReturnVoid_erasesTheReturnedType() {
        // UML holds the return type to be optional, and omitting it means "returns nothing".
        assertEquals("", rules("""
            { "omit": ["return void"] }
            """).nameDisplayedReturn(void.class));
    }

    @Test
    void omitReturnVoid_touchesTheReturnOnly() {
        Rules rules = rules("""
            { "omit": ["return void"] }
            """);

        // The same type asked for a field or a parameter keeps its name: there it carries what
        // what the entry has to say.
        assertEquals("void", rules.nameDisplayed(void.class));
    }

    @Test
    void omitReturnVoid_touchesNoOtherType() {
        // The rule holds for void only: erasing a useful return — "names(): String" becoming
        // "names()" — would make the drawing lose what the box exists to show.
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
        // The two rules coexist: one erases a return, the other renames a type.
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
        // And it names the faulty entry of the array, not the whole array.
        assertTrue(e.getMessage().contains("omit[0]"), e.getMessage());
    }

    // --- "abstract": the mark of an operation without implementation ---

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
        // "empty" serves to know whether a file was found: a written rule, whatever it
        // is, must get it out of there.
        assertFalse(rules("""
            { "omit": ["abstract"] }
            """).empty());
    }

    @Test
    void theSameOmitTwice_isRefused() {
        // The second would ask for nothing more; a file that repeats itself is a file that
        // nobody reads again.
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "omit": ["return void", "return void"] }
            """));

        assertTrue(refusal.getMessage().contains("already asked for"), refusal.getMessage());
    }

    @Test
    void hideReturn_noLongerExists() {
        // The return is no longer a matter of "hide": it makes no member vanish.
        assertThrows(IllegalArgumentException.class, () -> rules("""
            { "omit": ["return String"] }
            """));
    }

    // --- The file, on disk ---

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
        // It was written to be applied: ignoring it would give a diagram that is not
        // the one asked for, with nothing saying so.
        Files.writeString(tmp.resolve(Rules.FILE_NAME), "{ \"types\": }\n",
            StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> Rules.project(tmp));
    }

    // --- What the rules change in a box ---

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

    // The three kinds of method an interface can carry: abstract, default, static. The
    // the {abstract} mark must touch the first one only, before as after the rule.
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

        // What the rule does not touch: the method stays, with its return type, and
        // {static} — which says something else — survives.
        assertTrue(source.contains("+ state() : String"), source);
        assertTrue(source.contains("+ on() : boolean"), source);
        assertTrue(source.contains("{static} create()"), source);
    }

    @Test
    void omitAbstract_doesNotTouchTheBoxKeyword() {
        // "abstract" means the italics of an operation, not the word written at the head of a box:
        // an abstract class stays announced as such.
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

        // The rule also applies inside the angle brackets: the renaming bears on each
        // class name met, and not on the leading one only.
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

    // --- The "strategy" key, read or missing ---

    @Test
    void writtenStrategy_differsFromAMissingKey() {
        // "\"strategy\": \"none\"" and a missing key give the same drawing, but not the same
        // reporting: an empty box under a written key is a choice, the same box with no key
        // is a surprise the generation must explain.
        assertTrue(rules("""
            { "strategy": "none" }
            """).strategyDeclared());
        assertFalse(rules("{}").strategyDeclared());
    }

    // --- "order": the order of the members ---

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
        // Even when the second repeats the defect: two entries speaking of the same setting are
        // the start of a file nobody reads again. It is the parser that says so now.
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            {
              "order": "declaration",
              "order": "alphabetical"
            }
            """));

        assertTrue(refusal.getMessage().contains("umlg.json"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("order"), refusal.getMessage());
    }

    // --- "parameter names": short signatures ---

    @Test
    void parameterNames_fallSilent() {
        assertTrue(rules("""
            { "omit": ["parameter names"] }
            """).parametersOmitNames());
        assertFalse(rules("{}").parametersOmitNames());
    }

    // --- A pattern aims at methods and fields alike ---

    @Test
    void aPattern_aimsAtBothKindsOfMembers() {
        // There is no "hideMethods" and no "hideFields": a name almost always belongs
        // always to one or the other, and the entry says what it means without telling them apart.
        Rules read = rules("""
            { "hide": ["log", "get*"] }
            """);

        assertTrue(read.hiddenMember("log"));
        assertTrue(read.hiddenMember("getName"));
        assertFalse(read.hiddenMember("compute"));
    }

    @Test
    void twoPatternsInTheSameString_areRefused() {
        // The array expects one pattern per entry; two names in the same string would hide
        // neither one nor the other, and the message also points to "omit".
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["method get*"] }
            """));

        assertTrue(refusal.getMessage().contains("cannot contain a space"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("hide[0]"), refusal.getMessage());
    }

    // --- No rule designates a member by name ---

    @Test
    void qualifiedPattern_isRefusedPointingToUmlExclude() {
        // A rule does not designate an element by its name: that is the very split between the
        // the file and the annotations, and @UMLExclude is made for that case.
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["Representation.string"] }
            """));

        assertTrue(refusal.getMessage().contains("@UMLExclude"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("Representation.string"), refusal.getMessage());
    }

    @Test
    void ordinaryPattern_staysAccepted() {
        // The dot is the only refused character: "s*" does describe a family of names.
        assertTrue(rules("""
            { "hide": ["s*"] }
            """).hiddenMember("string"));
    }

    // --- The encoding of the file ---

    @Test
    void aFileThatIsNotUtf8_isRefusedSayingSo(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("umlg.json");
        // An accented note as a Windows editor would save it: the accent in CP-1252
        // is a lone byte, invalid in UTF-8 — which JSON requires. The first accent is enough
        // hence to failing the read, behind the generic wrapper of the generation and with no
        // that the message ever says the word "encoding".
        Files.write(file, ("{ \"_\": \"the café rules\", \"strategy\": \"public\" }\n")
            .getBytes(StandardCharsets.ISO_8859_1));

        var refusal = assertThrows(IllegalArgumentException.class, () -> Rules.file(file));

        assertTrue(refusal.getMessage().contains("UTF-8"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(file.toString()), refusal.getMessage());
    }

    @Test
    void qualifiedPatternWithStar_isRefusedToo() {
        // "Representation.*" designated the whole box of a named class: that is still a
        // naming by name, and the dot is enough to recognize it.
        assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["Representation.*"] }
            """));
    }

    @Test
    void aLeadingBom_isNamedAndNotTakenForAStrayNote() {
        // Decoding returns the mark as is, and the JSON parser then refused it as an
        // unexpected character — a refusal to which the reminder of the "_" convention was added, which
        // sent one looking for a note where there was none. The mark being invisible in a
        // editor, naming it is the only way of saying what to fix.
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> rules((char) 0xFEFF + "{ \"strategy\": \"public\" }"));

        assertTrue(refusal.getMessage().contains("byte order mark"), refusal.getMessage());
        assertFalse(refusal.getMessage().contains("\"_\""), refusal.getMessage());
    }

    // --- A pattern that takes everything ---

    @Test
    void patternMadeOfStarsOnly_isRefusedLikeTheLoneStar() {
        // The refusal bore on the letter written and not on what the pattern catches: "**"
        // translated into ".*.*", which takes any name just like "*". So it silently emptied
        // silence every compartment of every box, without even appearing in the report
        // dead rules — the pattern having indeed served.
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
        // The check aims at what takes everything, and nothing more: "*e*" carries two stars and
        // hides only the names containing an "e".
        Rules rules = rules("""
            { "hide": ["*e*"] }
            """);

        assertTrue(rules.hiddenMember("named"));
        assertFalse(rules.hiddenMember("total"));
    }

    // --- A space, in whatever form ---

    @Test
    void patternWithATab_isRefused() {
        // "contains(\" \")" saw only the ordinary space, so that a tabbed pattern was
        // accepted and then matched no Java name.
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "hide": ["get\\tNom"] }
            """));

        assertTrue(refusal.getMessage().contains("cannot contain a space"), refusal.getMessage());
    }

    @Test
    void patternWithANonBreakingSpace_isRefused() {
        // The non-breaking space is what a copy-paste from a word processor produces: it is the
        // the most likely case, and the only one review never catches.
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

    // --- A pattern written twice ---

    @Test
    void theSamePatternTwice_isRefused() {
        // "omit" already refused the duplicate; "hide" accepted it, and the second pattern hid
        // nothing more. Worse, a family of names written twice and aiming at nobody was
        // reported twice at the end of the scan, for a single entry to fix.
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

    // --- The "_" convention holds at every level ---

    @Test
    void noteInTypes_isNeitherARuleNorAFault() {
        // "types" is the second object of the format whose keys are written by hand, and the
        // the convention held there as elsewhere — except in the code. A one-word note became a
        // a dead renaming rule, and an ordinary note was refused by a message speaking
        // a space in a type name without ever mentioning the convention.
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

    // --- The reference to the schema ---

    @Test
    void schema_isNeitherARuleNorAFault() {
        // "$schema" is how an editor knows what to validate and what to complete. Refusing it
        // as an unknown key forced tuning the editor project by project, and the convention
        // "_" could not save it: a "_$schema" is precisely what no editor
        // reads.
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
        // The reference to the schema says nothing of the diagram: a file carrying it alone writes
        // as many rules as an empty file, that is, none.
        assertTrue(rules("""
            { "$schema": "./umlg-schema.json" }
            """).empty());
    }

    @Test
    void schemaInAView_isRefused() {
        // The reference holds for the file, and the file has one root: accepting it elsewhere
        // would suggest that a view is validated on its own.
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

    // --- The case of the enumerated words ---

    @Test
    void uppercaseStrategy_isRefusedLikeTheOtherWords() {
        // The tolerance came from no decision but from the toUpperCase valueOf demanded:
        // "PUBLIC" passed, while "Declaration", "Left-To-Right" and "Brackets" were
        // refused. A format where one word in five accepts uppercase teaches the reader a
        // a rule wrong four times out of five.
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
        // "project" is the reference a class writes to this key: the project cannot
        // point to itself.
        var refusal = assertThrows(IllegalArgumentException.class, () -> rules("""
            { "strategy": "project" }
            """));

        assertTrue(refusal.getMessage().contains("defer to this key"), refusal.getMessage());
    }

    // --- "empty" answers the question asked ---

    @Test
    void aWrittenDefault_doesNotMakeTheRulesEmpty() {
        // "empty" serves to know whether a file wrote anything. It compared the fifteen
        // settings at their default values, so that a file writing a default — which the
        // the format explicitly allows, a file saying aloud what it does being re-read
        // better — was answered "no rule".
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
        // A note is not a rule: a file writing only notes has asked for nothing.
        assertTrue(rules("""
            { "_": "nothing to ask for now", "_more": "really nothing" }
            """).empty());
    }
}
