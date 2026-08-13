package io.github.oliviercailloux.umlg.extraction.member;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.lang.reflect.Modifier;

public final class VisibilityPolicy {

    private VisibilityPolicy() {}

    public static boolean estVisible(int modifiers, UMLVisibility strategy, boolean hasInclude,
        boolean hasExclude) {

        if (hasExclude) {
            return false;
        }
        if (hasInclude) {
            return true;
        }

        return parLaStrategie(modifiers, strategy);
    }

    public static boolean parLaStrategie(int modifiers, UMLVisibility strategy) {
        return switch (strategy) {
            case NONE -> false;

            case PROJECT -> false;

            case PRIVATE -> true;
            case PUBLIC -> Modifier.isPublic(modifiers);
            case PROTECTED -> Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
            case PACKAGE -> Modifier.isPublic(modifiers) || estVisiblePaquetage(modifiers);
        };
    }

    private static boolean estVisiblePaquetage(int modifiers) {
        return !Modifier.isPublic(modifiers)
            && !Modifier.isProtected(modifiers)
            && !Modifier.isPrivate(modifiers);
    }

    static String symbole(int modifiers) {
        if (Modifier.isPublic(modifiers)) {
            return "+";
        }
        if (Modifier.isPrivate(modifiers)) {
            return "-";
        }
        if (Modifier.isProtected(modifiers)) {
            return "#";
        }
        return "~";
    }
}
