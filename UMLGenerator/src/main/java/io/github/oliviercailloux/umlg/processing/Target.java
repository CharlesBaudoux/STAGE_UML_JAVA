package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.extraction.relation.TargetResolver;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

final class Target {
    private final Reporter reporter;
    private final Types types;

    Target(Reporter reporter, Types types) {
        this.reporter = reporter;
        this.types = types;
    }

    String resolve(AnnotationMirror annotation, String member, String context, Element holder) {
        return resolve(annotation, member, context, holder, null,
            context + ": " + member + "() is not written, and it has no default.");
    }

    String resolve(AnnotationMirror annotation, String member, String context, Element holder,
        TypeMirror fallback, String complaintWithoutFallback) {
        AnnotationValue value = reporter.value(annotation, member);
        AnnotationValue position = reporter.valueWritten(annotation, member);

        TypeMirror target = (TypeMirror) value.getValue();

        if (target.getKind() == TypeKind.VOID) {
            if (fallback == null) {
                reporter.error(complaintWithoutFallback, holder, annotation);
                return null;
            }
            target = fallback;
        }

        if (target.getKind().isPrimitive()) {
            reporter.error(TargetResolver.messagePrimitiveTarget(context, target.toString()),
                holder, annotation, position);
            return null;
        }

        if (target.getKind() != TypeKind.DECLARED) {
            reporter.error(context + ": " + target + " is not a class; only a class of the "
                + "diagram can be an endpoint.", holder, annotation, position);
            return null;
        }

        TypeElement element = (TypeElement) types.asElement(target);

        if (element.getAnnotation(UMLClass.class) == null) {
            reporter.error(TargetResolver.messageTargetOutsideDiagram(context,
                element.getQualifiedName().toString(), member + "()"),
                holder, annotation, position);
            return null;
        }
        return element.getQualifiedName().toString();
    }

    String read(AnnotationMirror annotation, String member) {
        AnnotationValue value = reporter.value(annotation, member);
        if (value == null || !(value.getValue() instanceof TypeMirror type)
            || type.getKind() != TypeKind.DECLARED) {
            return null;
        }
        return ((TypeElement) types.asElement(type)).getQualifiedName().toString();
    }
}
