package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlNote;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoteExtractorTest {

    private static List<String> texts(UmlEntity entity) {
        return entity.notes().stream().map(UmlNote::text).toList();
    }

    @UMLClass
    @UMLNote(text = "Core of the domain")
    @UMLNote(text = "Do not expose outside the module", direction = UMLDirection.LEFT)
    static class Order {}

    @Test
    void classNotes_areAllKept() {
        UmlEntity entity = EntityExtractor.extract(Order.class);

        assertEquals(List.of("Core of the domain", "Do not expose outside the module"), texts(entity));
        assertTrue(entity.notes().stream().noneMatch(UmlNote::isOnMember));
    }

    @Test
    void writtenDirection_isKept() {
        UmlEntity entity = EntityExtractor.extract(Order.class);

        assertEquals(UMLDirection.NONE, entity.notes().get(0).direction());
        assertEquals(UMLDirection.LEFT, entity.notes().get(1).direction());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Account {

        @UMLNote(text = "Never negative")
        public double balance;

        @UMLNote(text = "Invisible, hence lost")
        private String passedWord;

        @UMLNote(text = "Algorithm critical", direction = UMLDirection.RIGHT)
        public void check() {}
    }

    @Test
    void memberNote_carriesTheMemberName() {
        UmlEntity entity = EntityExtractor.extract(Account.class);

        UmlNote note = entity.notes().stream()
            .filter(n -> n.text().equals("Never negative")).findFirst().orElseThrow();

        assertEquals("balance", note.anchor());
        assertTrue(note.isOnMember());
    }

    @Test
    void methodNote_carriesTheMethodName() {
        UmlEntity entity = EntityExtractor.extract(Account.class);

        assertEquals("check", entity.notes().stream()
            .filter(n -> n.text().equals("Algorithm critical")).findFirst().orElseThrow()
            .anchor());
    }

    @Test
    void noteAimingAtAHiddenMember_isDropped() {
        UmlEntity entity = EntityExtractor.extract(Account.class);

        assertTrue(texts(entity).stream().noneMatch(t -> t.contains("lost")),
            "The note of a hidden member must vanish: " + texts(entity));
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    static class Client {

        @UMLNote(text = "Aimed at by a link, not by a box")
        @UMLAssociation(target = Order.class)
        private Order order;
    }

    @Test
    void noteOnAFieldPromotedToLink_isDropped() {
        UmlEntity entity = EntityExtractor.extract(Client.class);

        assertEquals(List.of(), texts(entity));
    }

    @UMLNote(text = "   ")
    static class EmptyNote {}

    @Test
    void textMadeOfSpaces_isRefusedNamingTheLocation() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> NoteExtractor.extract(EmptyNote.class, List.of(), List.of()));

        assertTrue(e.getMessage().contains("EmptyNote"), e.getMessage());
        assertTrue(e.getMessage().contains("@UMLNote"), e.getMessage());
    }
}
