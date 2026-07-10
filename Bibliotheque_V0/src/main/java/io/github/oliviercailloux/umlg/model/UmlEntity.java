package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;


public record UmlEntity(
    String name,
    String stereotype,
    String color,
    UMLVisibility strategy,
    List<UmlField> fields,
    List<UmlMethod> methods
) {
    public UmlEntity {
        checkNotNull(name, "Le nom de l'entité ne peut pas être null.");
        checkNotNull(stereotype, "Le stéréotype ne peut pas être null (utiliser une chaîne vide).");
        checkNotNull(color, "La couleur ne peut pas être null (utiliser une chaîne vide).");
        checkNotNull(strategy, "La stratégie de visibilité ne peut pas être null.");
        fields = List.copyOf(checkNotNull(fields, "La liste des attributs ne peut pas être null."));
        methods = List.copyOf(checkNotNull(methods, "La liste des méthodes ne peut pas être null."));
    }
}
