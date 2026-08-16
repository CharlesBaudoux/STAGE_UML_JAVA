package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;

public record ViewDefinition(String name, ImmutableList<Selector> kept,
    ImmutableList<Selector> removed, UMLFlow direction) {
    public ViewDefinition {
        checkNotNull(name, "The view name cannot be null.");
        checkNotNull(kept, "The list of selectors cannot be null.");
        checkNotNull(removed, "The list of removed selectors cannot be null.");
        checkNotNull(direction, "The direction cannot be null; use UMLFlow.TOP_TO_BOTTOM.");

        checkArgument(!name.isBlank(), "The view name cannot be empty or made of whitespace only.");
        checkArgument(!kept.isEmpty(), "A view must retain at least one class.");
    }
}
