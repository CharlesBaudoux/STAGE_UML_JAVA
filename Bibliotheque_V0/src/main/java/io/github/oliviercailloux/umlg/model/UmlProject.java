package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

public record UmlProject(ImmutableSet<UmlEntity> entities) {

    public static UmlProject given(Set<UmlEntity> entities) {
        return new UmlProject(ImmutableSet.copyOf(entities));
    }

    public UmlProject {
        checkNotNull(entities, "L'ensemble des entités ne peut pas être null.");
        checkArgument(!entities.isEmpty(), "Le projet doit contenir au moins une classe annotée pour générer un diagramme.");
    }

    public ImmutableSet<UmlEntity> entities() {
        return entities;
    }
}