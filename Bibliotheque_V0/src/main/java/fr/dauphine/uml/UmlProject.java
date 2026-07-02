package fr.dauphine.uml;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

record UmlProject(ImmutableSet<UmlEntity> entities) {

    static UmlProject given(Set<UmlEntity> entities) {
        return new UmlProject(ImmutableSet.copyOf(entities));
    }

    UmlProject {
        checkNotNull(entities, "L'ensemble des entités ne peut pas être null.");
        checkArgument(!entities.isEmpty(), "Le projet doit contenir au moins une classe annotée pour générer un diagramme.");
    }

    public ImmutableSet<UmlEntity> entities() {
        return entities;
    }
}