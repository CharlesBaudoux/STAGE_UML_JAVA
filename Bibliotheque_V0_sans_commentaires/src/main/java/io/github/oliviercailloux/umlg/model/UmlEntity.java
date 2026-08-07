package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;

// Une boîte du diagramme : la traduction d'une classe annotée @UMLClass.
//
// Record, donc immuable : une fois construite, l'entité ne peut plus violer ses invariants. Les
// listes sont recopiées en ImmutableList à la construction, pour qu'une modification ultérieure
// de la liste passée par l'appelant ne puisse pas atteindre l'entité.
public record UmlEntity(
    String qualifiedName,
    String name,
    UmlEntityKind kind,
    String stereotype,
    String color,
    UMLVisibility strategy,
    ImmutableList<String> literals,
    ImmutableList<UmlField> fields,
    ImmutableList<UmlMethod> methods,
    ImmutableList<UmlRelation> relations,
    ImmutableList<UmlNote> notes,
    ImmutableList<String> constraints
) {
    public UmlEntity {
        checkNotNull(name, "The entity name cannot be null.");
        checkNotNull(qualifiedName, "The entity qualified name cannot be null.");
        checkNotNull(kind, "The entity kind cannot be null (use UmlEntityKind.CLASS).");
        checkNotNull(stereotype, "The stereotype cannot be null (use an empty string).");
        checkNotNull(color, "The color cannot be null (use an empty string).");
        checkNotNull(strategy, "The visibility strategy cannot be null.");
        checkArgument(!qualifiedName.isBlank(), "The entity qualified name cannot be empty.");

        literals = ImmutableList.copyOf(
            checkNotNull(literals, "The list of literals cannot be null."));
        fields = ImmutableList.copyOf(
            checkNotNull(fields, "The list of fields cannot be null."));
        methods = ImmutableList.copyOf(
            checkNotNull(methods, "The list of methods cannot be null."));
        relations = ImmutableList.copyOf(
            checkNotNull(relations, "The list of relations cannot be null."));
        notes = ImmutableList.copyOf(
            checkNotNull(notes, "The list of notes cannot be null."));
        constraints = Contraintes.validees(constraints);
    }

    public UmlEntity(String name, String stereotype, String color, UMLVisibility strategy,
        List<UmlField> fields, List<UmlMethod> methods) {
        this(name, name, UmlEntityKind.CLASS, stereotype, color, strategy,
            ImmutableList.of(),
            ImmutableList.copyOf(
                checkNotNull(fields, "The list of fields cannot be null.")),
            ImmutableList.copyOf(
                checkNotNull(methods, "The list of methods cannot be null.")),
            ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
    }

    public <T extends UmlRelation> ImmutableList<T> relations(Class<T> nature) {
        checkNotNull(nature);
        return relations.stream()
                        .filter(nature::isInstance)
                        .map(nature::cast)
                        .collect(ImmutableList.toImmutableList());
    }

    public ImmutableList<UmlAssociation> associations() {
        return relations(UmlAssociation.class);
    }

    public ImmutableList<UmlDependency> dependencies() {
        return relations(UmlDependency.class);
    }

    public ImmutableList<UmlInheritance> inheritances() {
        return relations(UmlInheritance.class);
    }

    public static Builder builder(String qualifiedName, String name) {
        return new Builder(qualifiedName, name);
    }

    public static final class Builder {

        private final String qualifiedName;
        private final String name;
        private UmlEntityKind kind = UmlEntityKind.CLASS;
        private String stereotype = "";
        private String color = "";
        private UMLVisibility strategy = UMLVisibility.NONE;
        private ImmutableList<String> literals = ImmutableList.of();
        private ImmutableList<UmlField> fields = ImmutableList.of();
        private ImmutableList<UmlMethod> methods = ImmutableList.of();
        private ImmutableList<UmlNote> notes = ImmutableList.of();
        private ImmutableList<String> constraints = ImmutableList.of();
        private final ImmutableList.Builder<UmlRelation> relations = ImmutableList.builder();

        private Builder(String qualifiedName, String name) {
            this.qualifiedName =
                checkNotNull(qualifiedName, "The entity qualified name cannot be null.");
            this.name = checkNotNull(name, "The entity name cannot be null.");
        }

        public Builder kind(UmlEntityKind kind) {
            this.kind = checkNotNull(kind);
            return this;
        }

        public Builder stereotype(String stereotype) {
            this.stereotype = checkNotNull(stereotype);
            return this;
        }

        public Builder color(String color) {
            this.color = checkNotNull(color);
            return this;
        }

        public Builder strategy(UMLVisibility strategy) {
            this.strategy = checkNotNull(strategy);
            return this;
        }

        public Builder literals(List<String> literals) {
            this.literals = ImmutableList.copyOf(checkNotNull(literals));
            return this;
        }

        public Builder fields(List<UmlField> fields) {
            this.fields = ImmutableList.copyOf(checkNotNull(fields));
            return this;
        }

        public Builder methods(List<UmlMethod> methods) {
            this.methods = ImmutableList.copyOf(checkNotNull(methods));
            return this;
        }

        public Builder notes(List<UmlNote> notes) {
            this.notes = ImmutableList.copyOf(checkNotNull(notes));
            return this;
        }

        public Builder constraints(List<String> constraints) {
            this.constraints = ImmutableList.copyOf(checkNotNull(constraints));
            return this;
        }

        public Builder relations(List<? extends UmlRelation> relations) {
            this.relations.addAll(checkNotNull(relations, "The list of relations cannot be null."));
            return this;
        }

        public Builder associations(List<UmlAssociation> associations) {
            return relations(associations);
        }

        public Builder dependencies(List<UmlDependency> dependencies) {
            return relations(dependencies);
        }

        public UmlEntity build() {
            return new UmlEntity(qualifiedName, name, kind, stereotype, color, strategy, literals,
                fields, methods, relations.build(), notes, constraints);
        }
    }
}
