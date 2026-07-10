package io.github.oliviercailloux.umlg.model;

public record UmlField(
    String name,
    String type,
    String visibility, 
    boolean isStatic
) {
}