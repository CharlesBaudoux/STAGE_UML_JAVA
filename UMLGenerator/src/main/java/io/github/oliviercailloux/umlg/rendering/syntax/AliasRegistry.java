package io.github.oliviercailloux.umlg.rendering.syntax;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import io.github.oliviercailloux.umlg.warning.Warner;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AliasRegistry {
    private final Set<String> aliasUsed = new HashSet<>();

    private final Map<String, String> aliasByQualifiedName = new HashMap<>();

    private final Map<String, String> namesDisplayedByName = new HashMap<>();

    public String register(String qualifiedName, String nameDisplayed) {
        checkNotNull(qualifiedName);
        checkNotNull(nameDisplayed);

        checkArgument(!aliasByQualifiedName.containsKey(qualifiedName),
            "Class \"%s\" is declared twice in the same diagram: the associations targeting it "
                + "could not tell which of the two boxes to point at.", qualifiedName);

        String homonym = namesDisplayedByName.put(nameDisplayed, qualifiedName);
        if (homonym != null) {
            Warner.say("\"" + nameDisplayed + "\" is the "
                + "name of two boxes of the same diagram (" + homonym + " and " + qualifiedName
                + "). They are distinct classes, but nothing on the drawing tells them apart, "
                + "nor which one a link reaches. Rename one with @UMLClass(name = ...), or split "
                + "them into two views.");
        }

        String alias = alias(nameDisplayed);
        aliasByQualifiedName.put(qualifiedName, alias);
        return alias;
    }

    public String aliasSource(String qualifiedName) {
        String alias = aliasByQualifiedName.get(checkNotNull(qualifiedName));
        checkState(alias != null,
            "Class \"%s\" has not been declared: call addClass on every entity before adding "
                + "their relations.", qualifiedName);
        return alias;
    }

    public String aliasTarget(String qualifiedName, String what) {
        String alias = aliasByQualifiedName.get(checkNotNull(qualifiedName));
        checkArgument(alias != null,
            "%s targets \"%s\", which is not part of the diagram. Check that this class carries "
                + "@UMLClass and that it sits in the scanned directory.", what, qualifiedName);
        return alias;
    }

    private String alias(String nameDisplayed) {
        String base = nameDisplayed.replaceAll("[^a-zA-Z0-9]", "");

        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "C" + base;
        }

        String candidate = base;
        for (int suffix = 2; !aliasUsed.add(candidate); suffix++) {
            candidate = base + "_" + suffix;
        }
        return candidate;
    }
}
