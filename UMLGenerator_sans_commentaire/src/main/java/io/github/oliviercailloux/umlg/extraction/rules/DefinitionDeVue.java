package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;

public record DefinitionDeVue(String nom, ImmutableList<Selecteur> retenus,
    ImmutableList<Selecteur> retires, UMLFlow direction) {

    public DefinitionDeVue {
        checkNotNull(nom, "The view name cannot be null.");
        checkNotNull(retenus, "The list of selectors cannot be null.");
        checkNotNull(retires, "The list of removed selectors cannot be null.");
        checkNotNull(direction, "The direction cannot be null; use UMLFlow.TOP_TO_BOTTOM.");

        checkArgument(!nom.isBlank(), "The view name cannot be empty or made of whitespace only.");
        checkArgument(!retenus.isEmpty(), "A view must retain at least one class.");
    }
}
