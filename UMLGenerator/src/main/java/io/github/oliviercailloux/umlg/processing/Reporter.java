package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

final class Reporter {
    private final Messager messager;
    private final Elements elements;

    private final boolean warn;

    Reporter(Messager messager, Elements elements, boolean warn) {
        this.messager = messager;
        this.elements = elements;
        this.warn = warn;
    }

    void error(String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message);
    }

    void configurationWarning(String message) {
        messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, message);
    }

    void error(String message, Element element, AnnotationMirror annotation,
        AnnotationValue value) {
        report(Diagnostic.Kind.ERROR, message, element, annotation, value);
    }

    void error(String message, Element element, AnnotationMirror annotation) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element, annotation);
    }

    void warning(String message, Element element, AnnotationMirror annotation,
        AnnotationValue value) {
        if (!warn) {
            return;
        }
        report(Diagnostic.Kind.WARNING, message, element, annotation, value);
    }

    void warning(String message, Element element, AnnotationMirror annotation) {
        if (!warn) {
            return;
        }
        messager.printMessage(Diagnostic.Kind.WARNING, message, element, annotation);
    }

    boolean requireBox(TypeElement box, String context, String consequence, Element holder,
        AnnotationMirror annotation) {
        if (box.getAnnotation(UMLClass.class) != null) {
            return true;
        }
        error(context + ": " + box.getQualifiedName() + " does not carry @UMLClass, so it is "
            + "not part of the diagram and " + consequence + ". Annotate it, or remove this "
            + "annotation.", holder, annotation);
        return false;
    }

    private void report(Diagnostic.Kind severity, String message, Element element,
        AnnotationMirror annotation, AnnotationValue value) {
        if (value == null) {
            messager.printMessage(severity, message, element, annotation);
            return;
        }
        messager.printMessage(severity, message, element, annotation, value);
    }

    AnnotationMirror mirror(Element element, Class<? extends Annotation> type) {
        String recherche = type.getCanonicalName();
        for (AnnotationMirror candidate : element.getAnnotationMirrors()) {
            if (candidate.getAnnotationType().toString().equals(recherche)) {
                return candidate;
            }
        }
        return null;
    }

    ImmutableList<AnnotationMirror> repeated(Element element, Class<? extends Annotation> type,
        Class<? extends Annotation> container) {
        ImmutableList.Builder<AnnotationMirror> result = ImmutableList.builder();

        AnnotationMirror only = mirror(element, type);
        if (only != null) {
            result.add(only);
        }

        AnnotationMirror group = mirror(element, container);
        if (group != null) {
            for (Object value : (List<?>) value(group, "value").getValue()) {
                result.add((AnnotationMirror) ((AnnotationValue) value).getValue());
            }
        }
        return result.build();
    }

    static String repeatedContext(String nameAnnotation, String location, int rank,
        int total) {
        String annotation = total == 1
            ? "@" + nameAnnotation
            : "the " + ordinal(rank + 1) + " @" + nameAnnotation;
        return annotation + " on " + location;
    }

    private static String ordinal(int rank) {
        String suffix = switch (rank % 100 >= 11 && rank % 100 <= 13 ? 0 : rank % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return rank + suffix;
    }

    AnnotationValue value(AnnotationMirror annotation, String member) {
        return find(elements.getElementValuesWithDefaults(annotation), member);
    }

    AnnotationValue valueWritten(AnnotationMirror annotation, String member) {
        return find(annotation.getElementValues(), member);
    }

    String text(AnnotationMirror annotation, String member) {
        AnnotationValue value = value(annotation, member);
        return value == null ? "" : String.valueOf(value.getValue());
    }

    private static AnnotationValue find(
        Map<? extends ExecutableElement, ? extends AnnotationValue> values, String member) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
            : values.entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(member)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
