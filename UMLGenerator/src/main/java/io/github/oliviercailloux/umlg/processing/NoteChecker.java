package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLNotes;
import io.github.oliviercailloux.umlg.model.UmlNote;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

final class NoteChecker {
    private final Reporter reporter;
    private final Strategy strategy;

    NoteChecker(Reporter reporter, Strategy strategy) {
        this.reporter = reporter;
        this.strategy = strategy;
    }

    void check(Element holder) {
        List<AnnotationMirror> notes =
            reporter.repeated(holder, UMLNote.class, UMLNotes.class);
        if (notes.isEmpty()) {
            return;
        }
        boolean onClass = holder.getKind().isClass() || holder.getKind().isInterface();
        TypeElement box = onClass
            ? (TypeElement) holder
            : (TypeElement) holder.getEnclosingElement();
        String location = onClass
            ? box.getQualifiedName().toString()
            : Member.location(holder);
        String context = "@UMLNote on " + location;

        UMLClass clazz = box.getAnnotation(UMLClass.class);
        if (!reporter.requireBox(box, context, "the note would have nothing to hang on",
            holder, notes.get(0))) {
            return;
        }

        for (int rank = 0; rank < notes.size(); rank++) {
            AnnotationMirror note = notes.get(rank);

            String noteContext = Reporter.repeatedContext("UMLNote", location, rank,
                notes.size());
            checkText(holder, note, noteContext);
            if (!onClass) {
                reportDirectionImpossible(holder, note, noteContext);
            }
        }

        if (!onClass) {
            reportAnchoringNotFound(holder, clazz, notes.get(0), context);
        }
    }

    private void reportDirectionImpossible(Element member, AnnotationMirror note,
        String context) {
        String direction = reporter.text(note, "direction");

        if (!direction.equals(UMLDirection.UP.name()) && !direction.equals(
            UMLDirection.DOWN.name())) {
            return;
        }
        reporter.warning(context + ": direction = " + direction + " has no effect on a "
            + "note attached to a member; PlantUML only places those to the left or to the right, "
            + "and the bubble will be drawn on the right. Use LEFT or RIGHT, or move the note "
            + "onto the class, where the four sides exist.", member, note,
            reporter.valueWritten(note, "direction"));
    }

    private void checkText(Element holder, AnnotationMirror note, String context) {
        try {
            new UmlNote(reporter.text(note, "text"), UMLDirection.NONE, "");
        } catch (IllegalArgumentException e) {
            reporter.error(context + " : " + e.getMessage(), holder, note,
                reporter.valueWritten(note, "text"));
        }
    }

    private void reportAnchoringNotFound(Element member, UMLClass clazz, AnnotationMirror note,
        String context) {
        if (member.getKind() == ElementKind.FIELD
            && reporter.mirror(member, UMLAssociation.class) != null) {
            reporter.warning(context + ": this field carries @UMLAssociation, so it is "
                + "drawn as a link and no longer sits inside the box; a note cannot hang on a "
                + "link. Move the note onto the class itself.", member, note);
            return;
        }

        if (strategy.memberDisplayed(member, clazz) != Strategy.Display.Hidden) {
            return;
        }
        reporter.warning(context + ": this member is not shown on the diagram "
            + "(strategy = "
            + strategy.effective(clazz) + " on the declaring class), so the note has "
            + "nothing to hang on and will not be drawn. Add @UMLInclude, or move the note onto "
            + "the class.", member, note);
    }
}
