package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

final class MemberChecker {
    private final Reporter reporter;
    private final Strategy strategy;

    MemberChecker(Reporter reporter, Strategy strategy) {
        this.reporter = reporter;
        this.strategy = strategy;
    }

    void check(Element member) {
        AnnotationMirror inclusion = reporter.mirror(member, UMLInclude.class);
        AnnotationMirror exclusion = reporter.mirror(member, UMLExclude.class);

        if (inclusion != null && exclusion != null) {
            reporter.error(Member.location(member) + " carries both @UMLInclude and "
                + "@UMLExclude: the intent is contradictory. Keep the one that says what you "
                + "mean.", member, exclusion);

            return;
        }

        AnnotationMirror present = inclusion != null ? inclusion : exclusion;
        if (present == null) {
            return;
        }

        TypeElement holder = (TypeElement) member.getEnclosingElement();
        if (!reporter.requireBox(holder, Member.location(member),
            "this annotation has no effect", member, present)) {
            return;
        }
        UMLClass clazz = holder.getAnnotation(UMLClass.class);

        if (inclusion != null && !checkRenaming(member, inclusion)) {
            return;
        }
        if (inclusion != null && !checkNotationAttribute(member, inclusion)) {
            return;
        }

        reportExemptionWithoutEffect(member, clazz, inclusion != null, present);
    }

    private boolean checkRenaming(Element member, AnnotationMirror inclusion) {
        String name = member.getAnnotation(UMLInclude.class).name();
        if (name.isEmpty()) {
            return true;
        }

        if (name.isBlank()) {
            reporter.error(Member.location(member) + ": @UMLInclude(name = ...) cannot be "
                + "made of whitespace only. Write a real name, or leave the attribute out.",
                member, inclusion);
            return false;
        }

        AnnotationMirror link = reporter.mirror(member, UMLAssociation.class);
        if (link != null && !Notation.stroke(reporter, link)
            && !member.getAnnotation(UMLAssociation.class).targetRole().isEmpty()) {
            reporter.error(Member.location(member) + " carries both targetRole = \""
                + member.getAnnotation(UMLAssociation.class).targetRole()
                + "\" and @UMLInclude(name = \"" + name + "\"): two names for the same line. "
                + "Keep only one of the two.", member, inclusion);
            return false;
        }
        if (name.equals(member.getSimpleName().toString())) {
            reporter.warning(Member.location(member) + ": @UMLInclude(name = \"" + name
                + "\") renames this member to the name it already has. Remove the attribute.",
                member, inclusion);
        }
        return true;
    }

    private boolean checkNotationAttribute(Element member, AnnotationMirror inclusion) {
        if (!member.getAnnotation(UMLInclude.class).asAttribute()) {
            return true;
        }
        String location = Member.location(member);

        if (member.getKind() != ElementKind.METHOD) {
            reporter.error(MemberExtractor.messageNotationAttributSurUnChamp(location),
                member, inclusion);
            return false;
        }
        ExecutableElement method = (ExecutableElement) member;
        if (!method.getParameters().isEmpty()) {
            reporter.error(MemberExtractor.messageNotationAttributAvecParametres(location),
                member, inclusion);
            return false;
        }
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            reporter.error(MemberExtractor.messageNotationAttributSansRetour(location),
                member, inclusion);
            return false;
        }
        return true;
    }

    private void reportExemptionWithoutEffect(Element member, UMLClass clazz, boolean inclusion,
        AnnotationMirror present) {
        AnnotationMirror link = reporter.mirror(member, UMLAssociation.class);
        boolean association = link != null;
        boolean stroke = association && Notation.stroke(reporter, link);

        if (Member.comesFromComponent(member)) {
            return;
        }

        Strategy.Display withoutExemption =
            strategy.memberDisplayedWithoutExemption(member, clazz);

        if (inclusion) {
            boolean renamed = !member.getAnnotation(UMLInclude.class).name().isEmpty();

            if (stroke) {
                reporter.warning(Member.location(member) + ": this field also carries "
                    + "@UMLAssociation, so it is drawn as a link between boxes and never inside "
                    + "one; @UMLInclude cannot bring it back into the attribute compartment. "
                    + "Remove one of the two annotations, or set notation = ATTRIBUTE on the "
                    + "association.", member, present);
                return;
            }
            if (association) {
                if (!renamed && strategy.rulesKnown()
                    && !strategy.champMasqueParUneRegle(member.getSimpleName().toString())) {
                    reporter.warning(Member.location(member) + ": @UMLInclude has no "
                        + "effect, this field carries @UMLAssociation(notation = ATTRIBUTE) and "
                        + "is therefore already shown in the attribute compartment, whatever "
                        + "the strategy. Remove it.", member, present);
                }
                return;
            }

            if (!renamed && strategy.rulesKnown() && Member.isAttribute(member)
                && withoutExemption == Strategy.Display.Displayed
                && !strategy.champMasqueParUneRegle(member.getSimpleName().toString())) {
                reporter.warning(Member.location(member) + ": @UMLInclude has no "
                    + "effect, the field is already shown by strategy = "
                    + strategy.effective(clazz)
                    + " on the declaring class. Remove it, or narrow the strategy.",
                    member, present);
            }
            return;
        }

        if (stroke) {
            reporter.warning(Member.location(member) + ": this field also carries "
                + "@UMLAssociation, so @UMLExclude removes the link between the two boxes, not "
                + "just a line inside one. Remove @UMLAssociation instead if that is what you "
                + "meant.", member, present);
            return;
        }
        if (association) {
            reporter.warning(Member.location(member) + ": this field also carries "
                + "@UMLAssociation(notation = ATTRIBUTE), so @UMLExclude removes the very line "
                + "that association asked for, and the target class keeps no trace of it. Remove "
                + "@UMLAssociation instead if that is what you meant.", member, present);
            return;
        }

        if (withoutExemption == Strategy.Display.Hidden) {
            reporter.warning(Member.location(member) + ": @UMLExclude has no effect, "
                + "strategy = " + strategy.effective(clazz)
                + " on the declaring class already hides "
                + "this member.", member, present);
        }
    }
}
