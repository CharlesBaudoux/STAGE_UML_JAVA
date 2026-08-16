package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.model.Multiplicities;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

final class MultiplicityChecker {
    private final Reporter reporter;
    private final Strategy strategy;

    MultiplicityChecker(Reporter reporter, Strategy strategy) {
        this.reporter = reporter;
        this.strategy = strategy;
    }

    void check(Element member) {
        AnnotationMirror annotation = reporter.mirror(member, UMLMultiplicity.class);
        if (annotation == null) {
            return;
        }
        TypeElement holder = (TypeElement) member.getEnclosingElement();
        if (!reporter.requireBox(holder, Member.location(member),
            "this annotation has no effect", member, annotation)) {
            return;
        }
        UMLClass clazz = holder.getAnnotation(UMLClass.class);

        if (!checkValue(member, annotation)) {
            return;
        }

        if (reporter.mirror(member, UMLAssociation.class) != null) {
            reporter.error(
                MemberExtractor.messageTwoMultiplicities(Member.location(member)),
                member, annotation);
            return;
        }
        reportMemberInvisible(member, clazz, annotation);
    }

    private boolean checkValue(Element member, AnnotationMirror annotation) {
        String value = reporter.text(annotation, "value");

        if (value.isEmpty()) {
            reporter.warning(Member.location(member) + ": @UMLMultiplicity(\"\") asks "
                + "for nothing; an empty multiplicity is what a member carries when it has none. "
                + "Write the multiplicity, or remove the annotation.", member, annotation);
            return false;
        }

        try {
            Multiplicities.validate(value, "multiplicity");
            return true;
        } catch (IllegalArgumentException e) {
            reporter.error(Member.location(member) + " : " + e.getMessage(), member,
                annotation, reporter.valueWritten(annotation, "value"));
            return false;
        }
    }

    private void reportMemberInvisible(Element member, UMLClass clazz,
        AnnotationMirror annotation) {
        if (strategy.memberDisplayed(member, clazz) != Strategy.Display.Hidden) {
            return;
        }
        reporter.warning(Member.location(member) + ": @UMLMultiplicity has no visible "
            + "effect, this member is not shown on the diagram (strategy = "
            + strategy.effective(clazz)
            + " on the declaring class). Add @UMLInclude if you want to see it, brackets "
            + "included.", member, annotation);
    }
}
