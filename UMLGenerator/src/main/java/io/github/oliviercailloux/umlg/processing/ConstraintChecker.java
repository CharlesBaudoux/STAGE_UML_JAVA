package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLConstraints;
import io.github.oliviercailloux.umlg.model.Constraints;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

final class ConstraintChecker {
    private final Reporter reporter;
    private final Strategy strategy;

    ConstraintChecker(Reporter reporter, Strategy strategy) {
        this.reporter = reporter;
        this.strategy = strategy;
    }

    void check(Element holder) {
        List<AnnotationMirror> constraints =
            reporter.repeated(holder, UMLConstraint.class, UMLConstraints.class);
        if (constraints.isEmpty()) {
            return;
        }
        boolean onClass = holder.getKind().isClass() || holder.getKind().isInterface();
        TypeElement box = onClass
            ? (TypeElement) holder
            : (TypeElement) holder.getEnclosingElement();
        String location =
            onClass ? box.getQualifiedName().toString() : Member.location(holder);
        String context = "@UMLConstraint on " + location;

        UMLClass clazz = box.getAnnotation(UMLClass.class);
        if (!reporter.requireBox(box, context, "this constraint would be written nowhere",
            holder, constraints.get(0))) {
            return;
        }

        for (int rank = 0; rank < constraints.size(); rank++) {
            checkRule(holder, constraints.get(rank), Reporter.repeatedContext(
                "UMLConstraint", location, rank, constraints.size()));
        }

        if (!onClass) {
            reportAttributeInvisible(holder, clazz, constraints.get(0), context);
        }
    }

    private void checkRule(Element holder, AnnotationMirror constraint, String context) {
        String rule = reporter.text(constraint, "rule");

        try {
            Constraints.validate(rule);
        } catch (IllegalArgumentException e) {
            reporter.error(context + " : " + e.getMessage(),
                holder, constraint, reporter.valueWritten(constraint, "rule"));
            return;
        }

        if (rule.startsWith("{") || rule.endsWith("}")) {
            reporter.warning(context + ": rule() = \"" + rule + "\" already carries the "
                + "braces, which the generator adds itself; the diagram would show them twice. "
                + "Write the rule bare.", holder, constraint,
                reporter.valueWritten(constraint, "rule"));
        }
    }

    private void reportAttributeInvisible(Element attribute, UMLClass clazz,
        AnnotationMirror constraint, String context) {
        if (reporter.mirror(attribute, UMLAssociation.class) != null) {
            return;
        }
        if (strategy.memberDisplayed(attribute, clazz) != Strategy.Display.Hidden) {
            return;
        }
        reporter.warning(context + ": this field is not shown on the diagram "
            + "(strategy = "
            + strategy.effective(clazz) + " on the declaring class), so the constraint "
            + "will not be written anywhere. Add @UMLInclude, or move the constraint onto the "
            + "class.", attribute, constraint);
    }
}
