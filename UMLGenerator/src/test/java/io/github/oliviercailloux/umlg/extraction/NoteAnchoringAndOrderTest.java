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

class NoteAnchoringAndOrderTest {

    private static List<String> texts(UmlEntity entity) {
        return entity.notes().stream().map(UmlNote::text).toList();
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Person {

        @UMLNote(text = "the note of an ordinary field")
        public int age;

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
        UmlEntity entity = EntityExtractor.extract(Renamed.class);

        assertEquals("identifier", entity.notes().get(0).anchor());
    }

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
        Rules rules = Rules.read("{ \"order\": \"declaration\" }", "umlg.json");

        assertEquals(List.of("zebra", "ostrich", "zoo", "arch"), notesUnder(rules));
    }
}
