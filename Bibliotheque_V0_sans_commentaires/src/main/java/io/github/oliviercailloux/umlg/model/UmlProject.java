package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

// Le diagramme entier : l'ensemble des boîtes, chacune portant ses propres relations.
//
// Un Set et non une List : deux fois la même entité n'aurait aucun sens, et l'ordre du rendu
// est décidé plus tard par PlantUmlSyntaxBuilder. ImmutableSet conserve tout de même l'ordre
// d'insertion, ce qui rend la sortie reproductible d'une exécution à l'autre.
public record UmlProject(ImmutableSet<UmlEntity> entities) {

    public static UmlProject given(Set<UmlEntity> entities) {
        return new UmlProject(ImmutableSet.copyOf(entities));
    }

    public UmlProject {
        checkNotNull(entities, "The set of entities cannot be null.");
        checkArgument(!entities.isEmpty(),
            "The project must contain at least one annotated class to generate a diagram.");
    }

}
