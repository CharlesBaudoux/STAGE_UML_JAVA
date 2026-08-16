package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

final class AssociationChecker {
    private static final ImmutableList<String> STROKE_PARAMETERS = ImmutableList.of(
        "type", "sourceMultiplicity", "sourceRole", "label", "direction");

    private final Reporter reporter;
    private final Target target;
    private final AttributeType typeAttribute;

    AssociationChecker(Reporter reporter, Target target, AttributeType typeAttribute) {
        this.reporter = reporter;
        this.target = target;
        this.typeAttribute = typeAttribute;
    }

    void check(Element field) {
        AnnotationMirror annotation = reporter.mirror(field, UMLAssociation.class);
        if (annotation == null) {
            return;
        }
        TypeElement holder = (TypeElement) field.getEnclosingElement();
        String context = "@UMLAssociation on " + holder.getQualifiedName() + "."
            + field.getSimpleName();

        if (!reporter.requireBox(holder, context,
            "this association would be drawn from nowhere", field, annotation)) {
            return;
        }

        String nameTarget = target.resolve(annotation, "target", context, field,
            typeAttribute.loneElement(field.asType()),
            typeAttribute.complaintTargetUninferable(context, field.asType()));
        if (nameTarget == null) {
            return;
        }

        if (!Notation.stroke(reporter, annotation)) {
            reportParametersWithoutRoom(field, annotation, context);
        }
        if (!checkValues(field, annotation, context, nameTarget)) {
            return;
        }
        reportInconsistencies(field, annotation, context);
    }

    private void reportParametersWithoutRoom(Element field, AnnotationMirror annotation,
        String context) {
        ImmutableList<String> written = STROKE_PARAMETERS.stream()
            .filter(member -> reporter.valueWritten(annotation, member) != null)
            .collect(ImmutableList.toImmutableList());

        if (written.isEmpty()) {
            return;
        }
        reporter.warning(context + ": notation = ATTRIBUTE writes this association "
            + "inside the box, as a line of the attribute compartment, which carries only a type "
            + "and a multiplicity. " + String.join(", ", written) + " decorate a path between two "
            + "boxes, and nothing will be drawn from them. Remove them, or switch back to "
            + "notation = ARROW.", field, annotation,
            reporter.valueWritten(annotation, "notation"));
    }

    private boolean checkValues(Element field, AnnotationMirror annotation, String context,
        String nameTarget) {
        try {
            UmlAssociation.builder(field.getSimpleName().toString(), nameTarget)
                .sourceMultiplicity(reporter.text(annotation, "sourceMultiplicity"))
                .targetMultiplicity(reporter.text(annotation, "targetMultiplicity"))
                .sourceRole(reporter.text(annotation, "sourceRole"))
                .targetRole(reporter.text(annotation, "targetRole"))
                .label(reporter.text(annotation, "label"))
                .build();
            return true;
        } catch (IllegalArgumentException e) {
            reporter.error(context + " : " + e.getMessage(), field, annotation);
            return false;
        }
    }

    private void reportInconsistencies(Element field, AnnotationMirror annotation, String context) {
        TypeMirror type = field.asType();
        AnnotationValue targetWritten = reporter.valueWritten(annotation, "target");

        if (targetWritten == null) {
            reportMultiplicity(field, annotation, context, type);
            return;
        }
        TypeMirror typeTarget = (TypeMirror) targetWritten.getValue();

        if (typeAttribute.comparableToTarget(type) && !typeAttribute.contains(type, typeTarget)) {
            reporter.warning(context + ": target = " + typeTarget + " does not appear "
                + "anywhere in the field's type (" + type + "). " + typeTarget + " is what will be "
                + "drawn all the same, since the annotation is what counts — check that this is "
                + "the class you meant.", field, annotation,
                reporter.valueWritten(annotation, "target"));
        }

        reportMultiplicity(field, annotation, context, type);
    }

    private void reportMultiplicity(Element field, AnnotationMirror annotation, String context,
        TypeMirror type) {
        String multiplicity = reporter.text(annotation, "targetMultiplicity");
        boolean multivalue = typeAttribute.multivalue(type);

        AnnotationValue position = reporter.valueWritten(annotation, "targetMultiplicity");

        if (multivalue && multiplicity.isEmpty()) {
            String holder = Notation.stroke(reporter, annotation) ? "link" : "attribute";

            reporter.warning(context + ": the field's type (" + type + ") holds several "
                + "objects, but targetMultiplicity is not set, so the " + holder + " will carry "
                + "no multiplicity at all. Only you know whether it is \"0..*\" or \"1..*\".",
                field, annotation, position);
            return;
        }
        if (multiplicity.isEmpty()) {
            return;
        }
        if (multivalue && !allowsSeveral(multiplicity)) {
            reporter.warning(context + ": targetMultiplicity = \"" + multiplicity
                + "\" allows at most one object, but the field's type (" + type + ") holds "
                + "several.", field, annotation, position);
            return;
        }
        if (!multivalue && allowsSeveral(multiplicity) && !typeAttribute.undetermined(type)) {
            reporter.warning(context + ": targetMultiplicity = \"" + multiplicity
                + "\" allows several objects, but the field's type (" + type + ") holds a single "
                + "one.", field, annotation, position);
        }
    }

    private static boolean allowsSeveral(String multiplicity) {
        int separator = multiplicity.indexOf("..");
        String highBound =
            separator < 0 ? multiplicity : multiplicity.substring(separator + 2);
        return highBound.equals("*") || Long.parseLong(highBound) > 1;
    }
}
