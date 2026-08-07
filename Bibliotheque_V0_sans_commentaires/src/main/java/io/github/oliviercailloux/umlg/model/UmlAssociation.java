package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Un trait entre deux boîtes : la traduction d'un attribut annoté @UMLAssociation.
//
// L'entité qui porte cette association en est la source ; la cible est désignée par son nom
// complet, et non par une référence à l'objet UmlEntity — le modèle reste ainsi constructible
// dans n'importe quel ordre, sans cycle de dépendance entre entités.
public record UmlAssociation(

    String fieldName,
    String targetQualifiedName,
    UMLRelationType type,
    String sourceCardinality,
    String targetCardinality,
    String sourceRole,
    String targetRole,
    boolean ordered,
    String qualifier,
    String label,
    UMLDirection direction,
    boolean derived,
    ImmutableList<String> constraints
) implements UmlRelation {

    private static final Pattern CARDINALITE =
        Pattern.compile("\\*|(\\d{1,9})(\\.\\.(\\d{1,9}|\\*))?");

    public UmlAssociation {
        checkNotNull(fieldName, "The name of the carrying field cannot be null.");
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(type, "The relation type cannot be null.");
        checkNotNull(sourceCardinality,
            "The source cardinality cannot be null (use an empty string).");
        checkNotNull(targetCardinality,
            "The target cardinality cannot be null (use an empty string).");
        checkNotNull(sourceRole, "The source role cannot be null (use an empty string).");
        checkNotNull(targetRole, "The target role cannot be null (use an empty string).");
        checkNotNull(qualifier, "The qualifier cannot be null (use an empty string).");
        checkNotNull(label, "The label cannot be null (use an empty string).");
        checkNotNull(direction, "The direction cannot be null (use UMLDirection.NONE).");

        checkArgument(!fieldName.isBlank(), "The name of the carrying field cannot be empty.");
        checkArgument(!targetQualifiedName.isBlank(), "The target qualified name cannot be empty.");

        validateCardinality(sourceCardinality, "sourceCardinality");
        validateCardinality(targetCardinality, "targetCardinality");

        validateTexteLibre(sourceRole, "sourceRole");
        validateTexteLibre(targetRole, "targetRole");
        validateTexteLibre(qualifier, "qualifier");
        validateTexteLibre(label, "label");

        constraints = Contraintes.validees(constraints);
    }

    public static Builder builder(String fieldName, String targetQualifiedName) {
        return new Builder(fieldName, targetQualifiedName);
    }

    static void validateCardinality(String cardinality, String parametre) {

        if (cardinality.isEmpty()) {
            return;
        }

        Matcher m = CARDINALITE.matcher(cardinality);
        checkArgument(m.matches(),
            "%s = \"%s\" is not a valid UML multiplicity (expected: an integer, \"*\", "
                + "or a range such as \"0..1\" or \"1..*\").",
            parametre, cardinality);

        String borneHaute = m.group(3);

        if (borneHaute == null || borneHaute.equals("*")) {
            return;
        }
        long min = Long.parseLong(m.group(1));
        long max = Long.parseLong(borneHaute);

        checkArgument(min <= max,
            "%s = \"%s\": the lower bound exceeds the upper bound.",
            parametre, cardinality);
    }

    private static void validateTexteLibre(String valeur, String parametre) {
        checkArgument(valeur.isEmpty() || !valeur.isBlank(),
            "%s cannot be made of whitespace only.", parametre);
    }

    public static final class Builder {

        private final String fieldName;
        private final String targetQualifiedName;
        private UMLRelationType type = UMLRelationType.ASSOCIATION;
        private String sourceCardinality = "";
        private String targetCardinality = "";
        private String sourceRole = "";
        private String targetRole = "";
        private boolean ordered = false;
        private String qualifier = "";
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

        public Builder type(UMLRelationType type) {
            this.type = checkNotNull(type);
            return this;
        }

        public Builder sourceCardinality(String cardinality) {
            this.sourceCardinality = checkNotNull(cardinality);
            return this;
        }

        public Builder targetCardinality(String cardinality) {
            this.targetCardinality = checkNotNull(cardinality);
            return this;
        }

        public Builder sourceRole(String role) {
            this.sourceRole = checkNotNull(role);
            return this;
        }

        public Builder targetRole(String role) {
            this.targetRole = checkNotNull(role);
            return this;
        }

        public Builder ordered(boolean ordered) {
            this.ordered = ordered;
            return this;
        }

        public Builder qualifier(String qualifier) {
            this.qualifier = checkNotNull(qualifier);
            return this;
        }

        public Builder label(String label) {
            this.label = checkNotNull(label);
            return this;
        }

        public Builder direction(UMLDirection direction) {
            this.direction = checkNotNull(direction);
            return this;
        }

        public Builder derived(boolean derived) {
            this.derived = derived;
            return this;
        }

        public Builder constraints(List<String> constraints) {
            this.constraints = ImmutableList.copyOf(checkNotNull(constraints));
            return this;
        }

        public UmlAssociation build() {
            return new UmlAssociation(fieldName, targetQualifiedName, type, sourceCardinality,
                targetCardinality, sourceRole, targetRole, ordered, qualifier, label, direction,
                derived, constraints);
        }
    }
}
