package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

final class DerivedChecker {
    private final Reporter reporter;
    private final Strategy strategy;

    DerivedChecker(Reporter reporter, Strategy strategy) {
        this.reporter = reporter;
        this.strategy = strategy;
    }

    void check(Element member) {
        AnnotationMirror annotation = reporter.mirror(member, UMLDerived.class);
        if (annotation == null) {
            return;
        }
        TypeElement holder = (TypeElement) member.getEnclosingElement();
        if (!reporter.requireBox(holder, Member.location(member),
            "this annotation has no effect", member, annotation)) {
            return;
        }
        UMLClass clazz = holder.getAnnotation(UMLClass.class);

        AnnotationMirror association = reporter.mirror(member, UMLAssociation.class);
        if (association != null) {
            if (Notation.stroke(reporter, association)) {
                reportLinkWithoutRole(member, annotation, association);
            }
            return;
        }
        reportMemberInvisible(member, clazz, annotation);
    }

    private void reportLinkWithoutRole(Element member, AnnotationMirror annotation,
        AnnotationMirror association) {
        if (!reporter.text(association, "targetRole").isEmpty()) {
            return;
        }
        reporter.warning(Member.location(member) + ": @UMLDerived marks the link as "
            + "derived, and the slash goes in front of the target role — but targetRole is not "
            + "set on @UMLAssociation, so nothing on the link would carry it. Set targetRole, or "
            + "remove @UMLDerived.", member, annotation);
    }

    private void reportMemberInvisible(Element member, UMLClass clazz,
        AnnotationMirror annotation) {
        if (strategy.memberDisplayed(member, clazz) != Strategy.Display.Hidden) {
            return;
        }
        reporter.warning(Member.location(member) + ": @UMLDerived has no visible "
            + "effect, this member is not shown on the diagram (strategy = "
            + strategy.effective(clazz)
            + " on the declaring class). Add @UMLInclude if you want to see it, slash included.",
            member, annotation);
    }
}
