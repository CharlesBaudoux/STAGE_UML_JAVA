package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.ClassHeaderValidator;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

final class ClassChecker {
    private final Reporter reporter;

    private final Map<String, String> firstBox = new HashMap<>();

    ClassChecker(Reporter reporter) {
        this.reporter = reporter;
    }

    void check(TypeElement clazz) {
        AnnotationMirror annotation = reporter.mirror(clazz, UMLClass.class);
        if (annotation == null) {
            return;
        }
        String context = "@UMLClass on " + clazz.getQualifiedName();

        checkNameWritten(clazz, annotation, context);
        checkStereotype(clazz, annotation, context);

        checkColor(clazz, annotation, context);
        reportStereotypeAlreadyFramed(clazz, annotation, context);
        reportDuplicateName(clazz, annotation, context);
    }

    private void checkNameWritten(Element holder, AnnotationMirror annotation, String context) {
        String value = reporter.text(annotation, "name");
        if (value.isEmpty()) {
            return;
        }
        report(holder, annotation, "name",
            () -> ClassHeaderValidator.validateName(value, context));
    }

    private void checkStereotype(Element holder, AnnotationMirror annotation,
        String context) {
        report(holder, annotation, "stereotype", () -> ClassHeaderValidator.validateStereotype(
            reporter.text(annotation, "stereotype"), context));
    }

    private void report(Element holder, AnnotationMirror annotation, String member,
        Runnable check) {
        try {
            check.run();
        } catch (IllegalArgumentException e) {
            reporter.error(e.getMessage(), holder, annotation,
                reporter.valueWritten(annotation, member));
        }
    }

    private void checkColor(Element holder, AnnotationMirror annotation, String context) {
        String color = reporter.text(annotation, "color");
        if (color.isEmpty()) {
            return;
        }
        try {
            ColorCodec.code(color, holder.toString());
        } catch (IllegalArgumentException e) {
            reporter.error(context + ": " + e.getMessage(), holder, annotation,
                reporter.valueWritten(annotation, "color"));
        }
    }

    private void reportStereotypeAlreadyFramed(Element holder, AnnotationMirror annotation,
        String context) {
        String stereotype = reporter.text(annotation, "stereotype");
        if (!stereotype.contains("<<") && !stereotype.contains(">>")) {
            return;
        }
        reporter.warning(context + ": stereotype() = \"" + stereotype + "\" already "
            + "contains the quotes, which the generator adds itself; the box would show them "
            + "twice. Write the bare word.", holder, annotation,
            reporter.valueWritten(annotation, "stereotype"));
    }

    private void reportDuplicateName(TypeElement clazz, AnnotationMirror annotation,
        String context) {
        String declare = reporter.text(annotation, "name");
        String displayed = declare.isEmpty() ? clazz.getSimpleName().toString() : declare;
        String qualified = clazz.getQualifiedName().toString();

        String previous = firstBox.putIfAbsent(displayed, qualified);

        if (previous == null || previous.equals(qualified)) {
            return;
        }
        reporter.warning(context + ": the box would be labelled \"" + displayed
            + "\", exactly like " + previous + ". The two are distinct classes, so the diagram "
            + "will show two boxes the reader cannot tell apart; set name() on one of them.",
            clazz, annotation, reporter.valueWritten(annotation, "name"));
    }
}
