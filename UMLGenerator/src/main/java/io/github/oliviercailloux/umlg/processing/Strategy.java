package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.VisibilityPolicy;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import javax.lang.model.element.Element;

final class Strategy {
    enum Display {
        Displayed,
        Hidden,
        Undecidable
    }

    private final Rules rules;

    Strategy(Rules rules) {
        this.rules = rules;
    }

    private boolean known(UMLClass clazz) {
        return clazz.strategy() != UMLVisibility.PROJECT || rules != null;
    }

    boolean rulesKnown() {
        return rules != null;
    }

    Display memberDisplayed(Element member, UMLClass clazz) {
        return decide(member, clazz,
            member.getAnnotation(UMLInclude.class) != null,
            member.getAnnotation(UMLExclude.class) != null);
    }

    Display memberDisplayedWithoutExemption(Element member, UMLClass clazz) {
        return decide(member, clazz, false, false);
    }

    private Display decide(Element member, UMLClass clazz, boolean inclusion,
        boolean exclusion) {
        if (!known(clazz)) {
            return Display.Undecidable;
        }

        if (Member.comesFromComponent(member)) {
            return Display.Displayed;
        }
        return VisibilityPolicy.isVisible(Member.bits(member), effective(clazz), inclusion,
            exclusion) ? Display.Displayed : Display.Hidden;
    }

    UMLVisibility effective(UMLClass clazz) {
        if (clazz.strategy() != UMLVisibility.PROJECT) {
            return clazz.strategy();
        }
        return rules == null ? UMLVisibility.NONE : rules.strategy();
    }

    boolean champMasqueParUneRegle(String name) {
        return rules != null && rules.hiddenMember(name);
    }
}
