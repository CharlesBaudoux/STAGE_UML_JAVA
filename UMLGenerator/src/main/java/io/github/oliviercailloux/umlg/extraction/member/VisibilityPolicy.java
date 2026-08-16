package io.github.oliviercailloux.umlg.extraction.member;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.lang.reflect.Modifier;

public final class VisibilityPolicy {
    private VisibilityPolicy() {}

    public static boolean isVisible(int modifiers, UMLVisibility strategy, boolean hasInclude,
        boolean hasExclude) {
        if (hasExclude) {
            return false;
        }
        if (hasInclude) {
            return true;
        }

        return byStrategy(modifiers, strategy);
    }

    public static boolean byStrategy(int modifiers, UMLVisibility strategy) {
        return switch (strategy) {
            case NONE -> false;

            case PROJECT -> false;

            case PRIVATE -> true;
            case PUBLIC -> Modifier.isPublic(modifiers);
            case PROTECTED -> Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
            case PACKAGE -> Modifier.isPublic(modifiers) || isVisiblePackage(modifiers);
        };
    }

    private static boolean isVisiblePackage(int modifiers) {
        return !Modifier.isPublic(modifiers)
            && !Modifier.isProtected(modifiers)
            && !Modifier.isPrivate(modifiers);
    }

    static String symbol(int modifiers) {
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
