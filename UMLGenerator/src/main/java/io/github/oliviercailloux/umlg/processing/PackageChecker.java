package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.extraction.PackageExtractor;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

final class PackageChecker {
    private final Reporter reporter;

    private final Map<String, Color> firstColor = new HashMap<>();

    private record Color(String value, String clazz) {}

    PackageChecker(Reporter reporter) {
        this.reporter = reporter;
    }

    void check(TypeElement clazz) {
        AnnotationMirror annotation = reporter.mirror(clazz, UMLPackage.class);
        if (annotation == null) {
            return;
        }
        String context = "@UMLPackage on " + clazz.getQualifiedName();

        if (!reporter.requireBox(clazz, context, "there is no box to group", clazz,
            annotation)) {
            return;
        }

        String name = reporter.text(annotation, "name");
        if (name.isBlank()) {
            reporter.error(PackageExtractor.messageNomDeGroupeVide(context), clazz,
                annotation, reporter.valueWritten(annotation, "name"));
            return;
        }

        if (!checkColor(clazz, annotation, context)) {
            return;
        }
        reportColorDisagreement(clazz, annotation, context, name);
    }

    private boolean checkColor(Element holder, AnnotationMirror annotation, String context) {
        String color = reporter.text(annotation, "color");
        if (color.isEmpty()) {
            return true;
        }
        try {
            ColorCodec.code(color, holder.toString());
        } catch (IllegalArgumentException e) {
            reporter.error(context + ": " + e.getMessage(), holder, annotation,
                reporter.valueWritten(annotation, "color"));
            return false;
        }
        return true;
    }

    private void reportColorDisagreement(TypeElement clazz, AnnotationMirror annotation,
        String context, String name) {
        String color = reporter.text(annotation, "color");

        if (color.isEmpty()) {
            return;
        }
        String qualified = clazz.getQualifiedName().toString();

        Color previous = firstColor.putIfAbsent(name, new Color(color, qualified));

        if (previous == null || previous.clazz().equals(qualified)
            || previous.value().equals(color)) {
            return;
        }
        reporter.warning(context + ": group \"" + name + "\" is already coloured \""
            + previous.value() + "\" by " + previous.clazz() + ". A rectangle has only "
            + "one colour, so the first one wins and this one will not be drawn.",
            clazz, annotation, reporter.valueWritten(annotation, "color"));
    }
}
