package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import java.util.List;

/**
 * A link between two boxes, as the drawing will tell it.
 *
 * @param fieldName the name of the field carrying the link.
 * @param targetQualifiedName the qualified name of the class aimed at.
 * @param type association, composition or aggregation.
 * @param sourceMultiplicity the multiplicity at the source endpoint, empty if there is none.
 * @param targetMultiplicity the multiplicity at the target endpoint.
 * @param sourceRole the role written at the source endpoint.
 * @param targetRole the role written at the target endpoint.
 * @param label the text written along the stroke.
 * @param direction which side to push the target box to.
 * @param derived true if the link is computed rather than stored.
 * @param constraints the rules written between braces on the stroke.
 */
public record UmlAssociation(

    String fieldName,
    String targetQualifiedName,
    UMLRelationType type,
    String sourceMultiplicity,
    String targetMultiplicity,
    String sourceRole,
    String targetRole,
    String label,
    UMLDirection direction,
    boolean derived,
    ImmutableList<String> constraints
) implements UmlRelation {
    /**
     * Checks that nothing is null and that multiplicities and texts are written the way UML writes them.
     */
    public UmlAssociation {
        checkNotNull(fieldName, "The name of the carrying field cannot be null.");
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(type, "The relation type cannot be null.");
        checkNotNull(sourceMultiplicity,
            "The source multiplicity cannot be null (use an empty string).");
        checkNotNull(targetMultiplicity,
            "The target multiplicity cannot be null (use an empty string).");
        checkNotNull(sourceRole, "The source role cannot be null (use an empty string).");
        checkNotNull(targetRole, "The target role cannot be null (use an empty string).");
        checkNotNull(label, "The label cannot be null (use an empty string).");
        checkNotNull(direction, "The direction cannot be null (use UMLDirection.NONE).");

        checkArgument(!fieldName.isBlank(), "The name of the carrying field cannot be empty.");
        checkArgument(!targetQualifiedName.isBlank(), "The target qualified name cannot be empty.");

        Multiplicities.validate(sourceMultiplicity, "sourceMultiplicity");
        Multiplicities.validate(targetMultiplicity, "targetMultiplicity");

        validateFreeText(sourceRole, "sourceRole");
        validateFreeText(targetRole, "targetRole");
        validateFreeText(label, "label");

        constraints = Constraints.validated(constraints);
    }

    /**
     * Opens the description of a link.
     *
     * @param fieldName the name of the field carrying the link.
     * @param targetQualifiedName the qualified name of the class aimed at.
     * @return how to describe the link, decoration by decoration.
     */
    public static Builder builder(String fieldName, String targetQualifiedName) {
        return new Builder(fieldName, targetQualifiedName);
    }

    private static void validateFreeText(String value, String parameter) {
        checkArgument(value.isEmpty() || !value.isBlank(),
            "%s cannot be made of whitespace only.", parameter);
    }

    /** How to describe a link without counting arguments. */
    public static final class Builder {
        private final String fieldName;
        private final String targetQualifiedName;
        private UMLRelationType type = UMLRelationType.ASSOCIATION;
        private String sourceMultiplicity = "";
        private String targetMultiplicity = "";
        private String sourceRole = "";
        private String targetRole = "";
        private String label = "";
        private UMLDirection direction = UMLDirection.NONE;
        private boolean derived = false;
        private ImmutableList<String> constraints = ImmutableList.of();

        private Builder(String fieldName, String targetQualifiedName) {
            this.fieldName =
                checkNotNull(fieldName, "The name of the carrying field cannot be null.");
            this.targetQualifiedName =
                checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        }

        /**
         * Writes the nature of the link.
         *
         * @param type association, composition or aggregation.
         * @return this builder.
         */
        public Builder type(UMLRelationType type) {
            this.type = checkNotNull(type);
            return this;
        }

        /**
         * Writes the multiplicity at the source endpoint.
         *
         * @param multiplicity the multiplicity at the source endpoint.
         * @return this builder.
         */
        public Builder sourceMultiplicity(String multiplicity) {
            this.sourceMultiplicity = checkNotNull(multiplicity);
            return this;
        }

        /**
         * Writes the multiplicity at the target endpoint.
         *
         * @param multiplicity the multiplicity at the target endpoint.
         * @return this builder.
         */
        public Builder targetMultiplicity(String multiplicity) {
            this.targetMultiplicity = checkNotNull(multiplicity);
            return this;
        }

        /**
         * Writes the role at the source endpoint.
         *
         * @param role the role written at the source endpoint.
         * @return this builder.
         */
        public Builder sourceRole(String role) {
            this.sourceRole = checkNotNull(role);
            return this;
        }

        /**
         * Writes the role at the target endpoint.
         *
         * @param role the role written at the target endpoint.
         * @return this builder.
         */
        public Builder targetRole(String role) {
            this.targetRole = checkNotNull(role);
            return this;
        }

        /**
         * Writes the text carried by the stroke.
         *
         * @param label the text written along the stroke.
         * @return this builder.
         */
        public Builder label(String label) {
            this.label = checkNotNull(label);
            return this;
        }

        /**
         * Constrains the placement of the target box.
         *
         * @param direction which side to push the target box to.
         * @return this builder.
         */
        public Builder direction(UMLDirection direction) {
            this.direction = checkNotNull(direction);
            return this;
        }

        /**
         * Says that the link is computed rather than stored.
         *
         * @param derived true if the link is computed rather than stored.
         * @return this builder.
         */
        public Builder derived(boolean derived) {
            this.derived = derived;
            return this;
        }

        /**
         * Writes the rules carried by the stroke.
         *
         * @param constraints the rules written between braces on the stroke.
         * @return this builder.
         */
        public Builder constraints(List<String> constraints) {
            this.constraints = ImmutableList.copyOf(checkNotNull(constraints));
            return this;
        }

        /** {@return the described link} */
        public UmlAssociation build() {
            return new UmlAssociation(fieldName, targetQualifiedName, type, sourceMultiplicity,
                targetMultiplicity, sourceRole, targetRole, label, direction, derived, constraints);
        }
    }
}
