package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlNote;
import java.util.List;
import org.junit.jupiter.api.Test;

// Where a bubble hangs, and in which order the bubbles come out.
//
// NoteExtractorTest already holds the general rule — a note is drawn only when hung on a
// a drawn member. Two points remain where the extractor did not hold it: a method that changes
// of compartment, and the order asked for by the project.
class NoteAnchoringAndOrderTest {

    private static List<String> texts(UmlEntity entity) {
        return entity.notes().stream().map(UmlNote::text).toList();
    }

    // --- A method written in attribute notation stays anchorable ---

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Person {

        @UMLNote(text = "the note of an ordinary field")
        public int age;

        // asAttribute = true: the method leaves the method compartment for the
        // fields. It is drawn — its note must be too.
        @UMLInclude(asAttribute = true)
        @UMLNote(text = "the note of a method in attribute form")
        public String name() {
            return "";
        }

        @UMLNote(text = "the note of an ordinary method")
        public void validate() {}
    }

    @Test
    void noteOnAMethodInAttributeNotation_isKept() {
        // The member appears in the field compartment, never in the method one:
        // looking for its name among the visible methods only silently threw its bubble away.
        UmlEntity entity = EntityExtractor.extract(Person.class);

        assertEquals(List.of("the note of an ordinary field",
            "the note of a method in attribute form", "the note of an ordinary method"),
            texts(entity));
    }

    @Test
    void noteOnAMethodInAttributeNotation_hangsOnTheDrawnName() {
        UmlEntity entity = EntityExtractor.extract(Person.class);

        UmlNote note = entity.notes().stream()
            .filter(n -> n.text().contains("method in attribute")).findFirst().orElseThrow();

        assertEquals("name", note.anchor());
        assertEquals(List.of("age", "name"),
            entity.fields().stream().map(UmlField::name).toList());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Renamed {

        @UMLInclude(asAttribute = true, name = "identifier")
        @UMLNote(text = "on the diagram name")
        public String id() {
            return "";
        }
    }

    @Test
    void noteOnARenamedMethod_hangsOnTheDiagramName() {
        // PlantUML hangs a bubble only on a name it has seen go by: that of the drawing,
        // not the code order.
        UmlEntity entity = EntityExtractor.extract(Renamed.class);

        assertEquals("identifier", entity.notes().get(0).anchor());
    }

    // --- The requested order holds for the bubbles too ---

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Unordered {

        @UMLNote(text = "zebra")
        public int zebra;

        @UMLNote(text = "ostrich")
        public int ostrich;

        @UMLNote(text = "zoo")
        public void zoo() {}

        @UMLNote(text = "arch")
        public void arch() {}
    }

    private static List<String> notesUnder(Rules rules) {
        ImmutableList<UmlField> fields =
            MemberExtractor.extractFields(Unordered.class, UMLVisibility.PUBLIC, rules);
        ImmutableList<UmlMethod> methods =
            MemberExtractor.extractMethods(Unordered.class, UMLVisibility.PUBLIC, rules);

        return NoteExtractor.extract(Unordered.class, fields, methods, rules).stream()
            .map(UmlNote::text).toList();
    }

    @Test
    void byDefault_theBubblesFollowTheAlphabet() {
        assertEquals(List.of("ostrich", "zebra", "arch", "zoo"), notesUnder(Rules.no()));
    }

    @Test
    void onRequest_theBubblesFollowTheCodeOrder() {
        // Fields filed in code order and bubbles filed by alphabet
        // would designate the same members in two different orders on the same drawing.
        Rules rules = Rules.read("{ \"order\": \"declaration\" }", "umlg.json");

        assertEquals(List.of("zebra", "ostrich", "zoo", "arch"), notesUnder(rules));
    }
}
