package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Une flèche pointillée : « la source se sert de la cible », sans la détenir.
//
// Beaucoup plus pauvre qu'une association — ni multiplicité, ni rôle, ni sens de possession —
// parce qu'une dépendance ne dit rien d'autre que l'existence du lien. Étant un record à deux
// composantes, son equals()/hashCode() est fourni par Java : c'est ce qui permet à
// DependencyExtractor de détecter les doublons avec un simple HashSet.
public record UmlDependency(String targetQualifiedName, String label) implements UmlRelation {

    public UmlDependency {
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(label, "The label cannot be null (use an empty string).");

        checkArgument(!targetQualifiedName.isBlank(),
            "The target qualified name cannot be empty.");

        checkArgument(label.isEmpty() || !label.isBlank(),
            "The label cannot be made of whitespace only.");
    }

    public static UmlDependency toTarget(String targetQualifiedName) {
        return new UmlDependency(targetQualifiedName, "");
    }
}
