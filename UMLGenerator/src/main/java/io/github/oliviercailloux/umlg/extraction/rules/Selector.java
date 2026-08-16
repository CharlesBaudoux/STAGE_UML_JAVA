package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public record Selector(Kind kind, String value) {
    public enum Kind {
        Class,

        Package,

        Neighborhood
    }

    public Selector {
        checkNotNull(kind);
        checkNotNull(value);
        checkArgument(!value.isBlank(), "A selector cannot be empty or made of whitespace only.");
        checkArgument(!value.contains(" "),
            "A selector cannot contain a space (read \"%s\").", value);
    }

    public static Selector clazz(String name) {
        return new Selector(Kind.Class, name);
    }

    public static Selector packageOf(String name) {
        return new Selector(Kind.Package, name);
    }

    public static Selector neighborhood(String name) {
        return new Selector(Kind.Neighborhood, name);
    }

    public String written() {
        return switch (kind) {
            case Class -> value;
            case Package -> "package " + value;
            case Neighborhood -> "around " + value;
        };
    }
}
