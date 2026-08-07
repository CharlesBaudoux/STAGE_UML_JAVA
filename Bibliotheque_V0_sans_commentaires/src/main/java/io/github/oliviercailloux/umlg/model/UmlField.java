package io.github.oliviercailloux.umlg.model;

import com.google.common.collect.ImmutableList;

// Une ligne du compartiment des attributs, par exemple "- solde : double {solde >= 0}".
public record UmlField(
    String name,
    String type,
    String visibility,
    boolean isStatic,
    boolean derived,
    ImmutableList<String> constraints
) {

    public UmlField {
        constraints = Contraintes.validees(constraints);
    }

    public UmlField(String name, String type, String visibility, boolean isStatic) {
        this(name, type, visibility, isStatic, false, ImmutableList.of());
    }
}
