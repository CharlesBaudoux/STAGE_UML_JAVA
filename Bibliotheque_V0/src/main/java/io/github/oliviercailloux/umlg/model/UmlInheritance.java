package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Un lien d'héritage : la traduction d'un {@code extends} ou d'un {@code implements}.
 *
 * <p>C'est la seule relation que l'utilisateur ne déclare pas. Les autres viennent d'une
 * annotation qu'il a écrite ; celle-ci se lit dans la signature de la classe, où elle est déjà
 * dite une fois pour toutes. Il n'y a donc rien à paramétrer, et le record ne porte que le strict
 * nécessaire : où va le trait, et de quelle nature il est.
 *
 * @param targetQualifiedName le nom complet du parent. Ni nul ni vide.
 * @param type généralisation ({@code extends}) ou réalisation ({@code implements}).
 */
public record UmlInheritance(String targetQualifiedName, UmlInheritanceType type)
    implements UmlRelation {

    /**
     * Vérifie les invariants du lien d'héritage.
     *
     * @throws NullPointerException si le nom de la cible ou la nature est nul.
     * @throws IllegalArgumentException si le nom de la cible est vide.
     */
    public UmlInheritance {
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(type, "The inheritance type cannot be null.");
        checkArgument(!targetQualifiedName.isBlank(), "The target qualified name cannot be empty.");
    }

    /**
     * {@link UmlRelation} exige un label ; un lien d'héritage n'en porte jamais.
     *
     * @return toujours la chaîne vide, qui dit l'absence : le rendu n'écrit alors rien après le
     *     trait.
     */
    @Override
    public String label() {
        return "";
    }

    /**
     * Un {@code extends} : la source est une sorte de la cible.
     *
     * <p>Deux fabriques plutôt que le constructeur : sur le site d'appel,
     * {@code generalization(X)} se lit mieux que {@code new UmlInheritance(X, GENERALIZATION)},
     * qui répète le type deux fois.
     *
     * @param targetQualifiedName le nom complet du parent.
     * @return le lien, à trait plein.
     */
    public static UmlInheritance generalization(String targetQualifiedName) {
        return new UmlInheritance(targetQualifiedName, UmlInheritanceType.GENERALIZATION);
    }

    /**
     * Un {@code implements} : la source s'engage à honorer le contrat de la cible.
     *
     * @param targetQualifiedName le nom complet de l'interface réalisée.
     * @return le lien, à trait pointillé.
     */
    public static UmlInheritance realization(String targetQualifiedName) {
        return new UmlInheritance(targetQualifiedName, UmlInheritanceType.REALIZATION);
    }
}
