package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;

/** What a constraint is allowed to be: a non-empty rule, written without its braces. */
public final class Constraints {
    private Constraints() {}

    /**
     * Refuses a constraint that is empty or already surrounded by braces.
     *
     * @param rule the rule written in the annotation.
     */
    public static void validate(String rule) {
        checkArgument(!checkNotNull(rule, "A constraint cannot be null.").isBlank(),
            "A constraint cannot be empty or made of whitespace only.");
    }

    static ImmutableList<String> validated(List<String> rules) {
        ImmutableList<String> frozen = ImmutableList.copyOf(
            checkNotNull(rules, "The list of constraints cannot be null."));

        for (String rule : frozen) {
            validate(rule);
        }
        return frozen;
    }
}
