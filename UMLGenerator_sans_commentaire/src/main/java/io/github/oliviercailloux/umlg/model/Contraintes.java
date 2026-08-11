package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;

final class Contraintes {

    private Contraintes() {}

    static ImmutableList<String> validees(List<String> regles) {
        ImmutableList<String> figees = ImmutableList.copyOf(
            checkNotNull(regles, "The list of constraints cannot be null."));

        for (String regle : figees) {
            checkArgument(!regle.isBlank(),
                "A constraint cannot be empty or made of whitespace only.");
        }
        return figees;
    }
}
