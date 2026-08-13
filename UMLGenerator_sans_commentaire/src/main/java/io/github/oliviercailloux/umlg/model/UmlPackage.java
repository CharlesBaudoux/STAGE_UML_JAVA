package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Le groupe visuel auquel une boîte appartient : un rectangle nommé qui l'entoure.
 *
 * <p>Ce n'est pas le paquetage Java de la classe, et le nom du record ne doit pas tromper : il
 * suit celui de l'annotation, {@code @UMLPackage}, comme partout ailleurs dans le modèle. Le
 * groupe est déclaré, jamais déduit — le découpage qui fait un bon diagramme n'est pas toujours
 * celui qui fait une bonne arborescence de fichiers.
 *
 * <p>L'identité du groupe est son nom : deux entités portant le même {@link #name()} entrent dans
 * le même rectangle, quelles que soient leurs classes d'origine.
 *
 * @param name le nom du rectangle, ou la chaîne vide pour « aucun groupe ».
 * @param color sa couleur de remplissage, ou la chaîne vide.
 */
public record UmlPackage(String name, String color) {

    private static final UmlPackage AUCUN = new UmlPackage("", "");

    /**
     * Vérifie les invariants du groupe.
     *
     * @throws NullPointerException si le nom ou la couleur est nul ; leur absence se dit avec la
     *     chaîne vide.
     * @throws IllegalArgumentException si l'un des deux n'est fait que d'espaces, ou si une
     *     couleur est donnée sans nom de groupe : elle ne serait posée sur rien.
     */
    public UmlPackage {
        checkNotNull(name, "The group name cannot be null (use an empty string for no group).");
        checkNotNull(color, "The group color cannot be null (use an empty string).");

        checkArgument(name.isEmpty() || !name.isBlank(),
            "The group name cannot be made of whitespace only.");
        checkArgument(color.isEmpty() || !color.isBlank(),
            "The group color cannot be made of whitespace only.");
        checkArgument(!name.isEmpty() || color.isEmpty(),
            "A group color was given without a group name; there would be nothing to paint.");
    }

    /**
     * Ce que porte une entité qu'aucune {@code @UMLPackage} ne range : elle se dessine au premier
     * niveau du diagramme, hors de tout rectangle.
     *
     * @return le groupe vide, partagé par toutes les entités non rangées.
     */
    public static UmlPackage none() {
        return AUCUN;
    }

    /**
     * Ce groupe dessine-t-il un rectangle ?
     *
     * @return vrai si un rectangle entoure les boîtes de ce groupe.
     */
    public boolean isGrouped() {
        return !name.isEmpty();
    }
}
