package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Une flèche pointillée : « la source se sert de la cible », sans la détenir.
 *
 * <p>Beaucoup plus pauvre qu'une {@link UmlAssociation} — ni multiplicité, ni rôle, ni sens de
 * possession — parce qu'une dépendance ne dit rien d'autre que l'existence du lien. Étant un
 * record à deux composantes, son {@code equals()}/{@code hashCode()} est fourni par Java : c'est
 * ce qui permet à l'extraction de détecter les doublons avec un simple {@code HashSet}.
 *
 * @param targetQualifiedName le nom complet de la boîte dont la source se sert. Ni nul ni vide.
 * @param label le texte posé sur la flèche, ou la chaîne vide.
 */
public record UmlDependency(String targetQualifiedName, String label) implements UmlRelation {

    /**
     * Vérifie les invariants de la dépendance.
     *
     * @throws NullPointerException si le nom de la cible ou le label est nul ; l'absence de label
     *     se dit avec la chaîne vide.
     * @throws IllegalArgumentException si le nom de la cible est vide, ou si le label n'est fait
     *     que d'espaces.
     */
    public UmlDependency {
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(label, "The label cannot be null (use an empty string).");

        checkArgument(!targetQualifiedName.isBlank(),
            "The target qualified name cannot be empty.");

        checkArgument(label.isEmpty() || !label.isBlank(),
            "The label cannot be made of whitespace only.");
    }

    /**
     * Le cas ordinaire : une dépendance sans texte sur la flèche.
     *
     * @param targetQualifiedName le nom complet de la boîte dont la source se sert.
     * @return la dépendance, sans label.
     */
    public static UmlDependency toTarget(String targetQualifiedName) {
        return new UmlDependency(targetQualifiedName, "");
    }
}
