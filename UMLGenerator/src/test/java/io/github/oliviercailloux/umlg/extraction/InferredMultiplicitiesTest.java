package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InferredMultiplicitiesTest {

    private static final Rules BRACKETS =
        Rules.read("""
            { "multiplicity": "brackets" }
            """, "umlg.json");

    static class Fixture {
        public Set<String> aliases;
        public Optional<Integer> ucp;
        public Map<String, Integer> table;
        public String name;

        @UMLMultiplicity("1..*")
        public List<String> mandatory;

        public Set<String> names() {
            return Set.of();
        }
    }

    private static UmlField field(String name) {
        return MemberExtractor.extractFields(Fixture.class, UMLVisibility.PUBLIC, BRACKETS)
            .stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void aSet_isWrittenElementAndStar() {
        assertEquals("String", field("aliases").type());
        assertEquals("0..*", field("aliases").multiplicity());
    }

    @Test
    void anOptional_isWrittenUnwrappedElementAndZeroOrOne() {
        assertEquals("int", field("ucp").type());
        assertEquals("0..1", field("ucp").multiplicity());
    }

    @Test
    void aMap_staysWrittenInFull() {
        assertEquals("Map<String, Integer>", field("table").type());
        assertEquals("", field("table").multiplicity());
    }

    @Test
    void aSimpleType_receivesNothing() {
        assertEquals("String", field("name").type());
        assertEquals("", field("name").multiplicity());
    }

    @Test
    void aDeclaredMultiplicity_winsOverInference() {
        assertEquals("1..*", field("mandatory").multiplicity());
        assertEquals("String", field("mandatory").type());
    }

    @Test
    void withoutTheRule_nothingIsTranslated() {
        List<UmlField> fields =
            MemberExtractor.extractFields(Fixture.class, UMLVisibility.PUBLIC, Rules.no());
        UmlField aliases =
            fields.stream().filter(f -> f.name().equals("aliases")).findFirst().orElseThrow();

        assertEquals("Set<String>", aliases.type());
        assertEquals("", aliases.multiplicity());
    }

    @Test
    void aMethodReturn_isTranslatedToo() {
        List<UmlMethod> methods =
            MemberExtractor.extractMethods(Fixture.class, UMLVisibility.PUBLIC, BRACKETS);
        UmlMethod names =
            methods.stream().filter(m -> m.name().equals("names")).findFirst().orElseThrow();

        assertEquals("String", names.returnType());
        assertEquals("0..*", names.multiplicity());
    }

    @Test
    void aFormOtherThanBrackets_isRefusedNamingTheKey() {
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> Rules.read("""
                { "multiplicity": "stars" }
                """, "umlg.json"));

        assertTrue(refusal.getMessage().contains("multiplicity"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("brackets"), refusal.getMessage());
    }

    @Test
    void theDuplicateRule_isRefusedByTheParser() {
        var refusal = assertThrows(IllegalArgumentException.class,
            () -> Rules.read("""
                { "multiplicity": "brackets", "multiplicity": "brackets" }
                """,
                "umlg.json"));

        assertTrue(refusal.getMessage().contains("multiplicity"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("umlg.json"), refusal.getMessage());
    }
}
