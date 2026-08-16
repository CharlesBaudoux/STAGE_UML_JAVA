package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;

/**
 * One box of the diagram, with everything it shows.
 *
 * @param qualifiedName the qualified name of the class, which identifies the box.
 * @param name the name written in the box.
 * @param kind class, abstract class, interface, enumeration or annotation.
 * @param stereotype the stereotype written above the name, empty if there is none.
 * @param color the background colour, empty for the theme's own.
 * @param strategy how far down the visibilities to go when filling the box.
 * @param literals the values of an enumeration.
 * @param fields the displayed fields.
 * @param methods the displayed methods.
 * @param relations the links leaving this box.
 * @param notes the bubbles attached to the box or to its members.
 * @param constraints the rules written between braces.
 * @param group the rectangle surrounding the box, if there is one.
 * @param typeParameters the type parameters written after the name.
 */
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
    ImmutableList<String> constraints,
    UmlPackage group,
    String typeParameters
) {
    /** Checks that nothing is null and that the box carries a name. */
    public UmlEntity {
        checkNotNull(name, "The entity name cannot be null.");
        checkNotNull(qualifiedName, "The entity qualified name cannot be null.");
        checkNotNull(kind, "The entity kind cannot be null (use UmlEntityKind.CLASS).");

        checkNotNull(stereotype, "The stereotype cannot be null (use an empty string).");
        checkNotNull(color, "The color cannot be null (use an empty string).");
        checkNotNull(strategy, "The visibility strategy cannot be null.");
        checkArgument(!qualifiedName.isBlank(), "The entity qualified name cannot be empty.");

        checkArgument(!name.isBlank(), "The entity name cannot be empty.");

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
        constraints = Constraints.validated(constraints);
        checkNotNull(group, "The group cannot be null (use UmlPackage.none()).");
        checkNotNull(typeParameters, "The type parameters cannot be null (use \"\").");
    }

    /**
     * A box with no link, no note and no group.
     *
     * @param name the name written in the box, which also serves as the qualified name.
     * @param stereotype the stereotype written above the name.
     * @param color the background colour.
     * @param strategy how far down the visibilities to go.
     * @param fields the displayed fields.
     * @param methods the displayed methods.
     */
    public UmlEntity(String name, String stereotype, String color, UMLVisibility strategy,
        List<UmlField> fields, List<UmlMethod> methods) {
        this(name, name, UmlEntityKind.CLASS, stereotype, color, strategy,
            ImmutableList.of(),
            ImmutableList.copyOf(
                checkNotNull(fields, "The list of fields cannot be null.")),
            ImmutableList.copyOf(
                checkNotNull(methods, "The list of methods cannot be null.")),
            ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), UmlPackage.none(),
            "");
    }

    /**
     * Attaches its links to an already described box.
     *
     * @param relations the links leaving this box.
     * @return the same box, with those links.
     */
    public UmlEntity withRelations(ImmutableList<UmlRelation> relations) {
        checkNotNull(relations, "The list of relations cannot be null.");
        if (relations.equals(this.relations)) {
            return this;
        }
        return new UmlEntity(qualifiedName, name, kind, stereotype, color, strategy, literals,
            fields, methods, relations, notes, constraints, group, typeParameters);
    }

    /**
     * The links of a given kind.
     *
     * @param <T> the kind of link asked for.
     * @param nature the class of that kind of link.
     * @return the links of that kind, in the order they were read.
     */
    public <T extends UmlRelation> ImmutableList<T> relations(Class<T> nature) {
        checkNotNull(nature);
        return relations.stream()
                        .filter(nature::isInstance)
                        .map(nature::cast)
                        .collect(ImmutableList.toImmutableList());
    }

    /** {@return the associations leaving this box} */
    public ImmutableList<UmlAssociation> associations() {
        return relations(UmlAssociation.class);
    }

    /** {@return the dependencies of this box} */
    public ImmutableList<UmlDependency> dependencies() {
        return relations(UmlDependency.class);
    }

    /** {@return the inheritances and realizations of this box} */
    public ImmutableList<UmlInheritance> inheritances() {
        return relations(UmlInheritance.class);
    }

    /** {@return the placement constraints set by this box} */
    public ImmutableList<UmlLayout> layouts() {
        return relations(UmlLayout.class);
    }

    /** {@return the association classes this box declares} */
    public ImmutableList<UmlAssociationClass> associationClasses() {
        return relations(UmlAssociationClass.class);
    }

    /**
     * Opens the description of a box.
     *
     * @param qualifiedName the qualified name of the class.
     * @param name the name written in the box.
     * @return how to describe the box, setting by setting.
     */
    public static Builder builder(String qualifiedName, String name) {
        return new Builder(qualifiedName, name);
    }

    /** How to describe a box without counting arguments. */
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
        private UmlPackage group = UmlPackage.none();
        private String typeParameters = "";

        private final ImmutableList.Builder<UmlRelation> relations = ImmutableList.builder();

        private Builder(String qualifiedName, String name) {
            this.qualifiedName =
                checkNotNull(qualifiedName, "The entity qualified name cannot be null.");
            this.name = checkNotNull(name, "The entity name cannot be null.");
        }

        /**
         * Writes what the box is.
         *
         * @param kind what the box is.
         * @return this builder.
         */
        public Builder kind(UmlEntityKind kind) {
            this.kind = checkNotNull(kind);
            return this;
        }

        /**
         * Writes the stereotype displayed above the name.
         *
         * @param stereotype the stereotype written above the name.
         * @return this builder.
         */
        public Builder stereotype(String stereotype) {
            this.stereotype = checkNotNull(stereotype);
            return this;
        }

        /**
         * Writes the background colour.
         *
         * @param color the background colour.
         * @return this builder.
         */
        public Builder color(String color) {
            this.color = checkNotNull(color);
            return this;
        }

        /**
         * Writes how far down the visibilities to go.
         *
         * @param strategy how far down the visibilities to go.
         * @return this builder.
         */
        public Builder strategy(UMLVisibility strategy) {
            this.strategy = checkNotNull(strategy);
            return this;
        }

        /**
         * Writes the values of an enumeration.
         *
         * @param literals the values of an enumeration.
         * @return this builder.
         */
        public Builder literals(List<String> literals) {
            this.literals = ImmutableList.copyOf(checkNotNull(literals));
            return this;
        }

        /**
         * Writes the displayed fields.
         *
         * @param fields the displayed fields.
         * @return this builder.
         */
        public Builder fields(List<UmlField> fields) {
            this.fields = ImmutableList.copyOf(checkNotNull(fields));
            return this;
        }

        /**
         * Writes the displayed methods.
         *
         * @param methods the displayed methods.
         * @return this builder.
         */
        public Builder methods(List<UmlMethod> methods) {
            this.methods = ImmutableList.copyOf(checkNotNull(methods));
            return this;
        }

        /**
         * Writes the bubbles attached to the box or to its members.
         *
         * @param notes the bubbles attached to the box or to its members.
         * @return this builder.
         */
        public Builder notes(List<UmlNote> notes) {
            this.notes = ImmutableList.copyOf(checkNotNull(notes));
            return this;
        }

        /**
         * Writes the rules carried by the box.
         *
         * @param constraints the rules written between braces.
         * @return this builder.
         */
        public Builder constraints(List<String> constraints) {
            this.constraints = ImmutableList.copyOf(checkNotNull(constraints));
            return this;
        }

        /**
         * Files the box in a named rectangle.
         *
         * @param group the rectangle surrounding the box.
         * @return this builder.
         */
        public Builder group(UmlPackage group) {
            this.group = checkNotNull(group);
            return this;
        }

        /**
         * Writes the links leaving the box.
         *
         * @param relations the links, of whatever kind they are.
         * @return this builder.
         */
        public Builder relations(List<? extends UmlRelation> relations) {
            this.relations.addAll(checkNotNull(relations, "The list of relations cannot be null."));
            return this;
        }

        /**
         * Writes links, all of them associations.
         *
         * @param associations the associations leaving the box.
         * @return this builder.
         */
        public Builder associations(List<UmlAssociation> associations) {
            return relations(associations);
        }

        /**
         * Writes links, all of them dependencies.
         *
         * @param dependencies the dependencies of the box.
         * @return this builder.
         */
        public Builder dependencies(List<UmlDependency> dependencies) {
            return relations(dependencies);
        }

        /**
         * Writes the type parameters displayed after the name.
         *
         * @param typeParameters the type parameters, as they are written.
         * @return this builder.
         */
        public Builder typeParameters(String typeParameters) {
            this.typeParameters = checkNotNull(typeParameters);
            return this;
        }

        /** {@return the described box} */
        public UmlEntity build() {
            return new UmlEntity(qualifiedName, name, kind, stereotype, color, strategy, literals,
                fields, methods, relations.build(), notes, constraints, group,
                typeParameters);
        }
    }
}
