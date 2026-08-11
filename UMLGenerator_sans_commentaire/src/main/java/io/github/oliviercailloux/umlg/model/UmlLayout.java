package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;

/**
 * Un lien invisible, posé pour contraindre le placement d'une boîte.
 *
 * <p>C'est la seule relation du modèle qui ne se voie pas sur le dessin, et la seule qui ne dise
 * rien du domaine. Elle est pourtant une relation à part entière : elle relie deux boîtes, se
 * résout par les mêmes alias, et s'écrit sur une ligne du même fichier. La ranger ailleurs
 * obligerait à dupliquer tout cela pour un cas unique.
 *
 * <p><b>Aucune annotation ne la produit.</b> Elle l'était, par une {@code @UMLLayout} que le
 * développeur posait sur une classe pour corriger un placement. L'annotation a été retirée : elle
 * ne disait rien qu'un diagramme puisse signifier, et l'enchaînement des blocs d'une vue fait
 * désormais mieux le peu qu'elle faisait bien — il ordonne ce que le moteur ne peut pas deviner,
 * sans demander à l'auteur de tenir à jour une carte de placements que le moindre ajout de classe
 * périme.
 *
 * <p>Ce qui subsiste ici est donc engendré, jamais écrit : la construction des vues en pose un
 * entre deux blocs consécutifs, avec {@code UMLDirection.NONE}.
 *
 * @param targetQualifiedName le nom complet de la boîte à placer. Ni nul ni vide.
 * @param direction le sens dans lequel la contraindre, ou {@code NONE}.
 */
public record UmlLayout(String targetQualifiedName, UMLDirection direction)
    implements UmlRelation {

    /**
     * Vérifie les invariants du placement.
     *
     * @throws NullPointerException si le nom de la cible ou la direction est nul.
     * @throws IllegalArgumentException si le nom de la cible est vide.
     */
    public UmlLayout {
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(direction, "The direction cannot be null (use UMLDirection.NONE).");

        checkArgument(!targetQualifiedName.isBlank(),
            "The target qualified name cannot be empty.");
    }

    /**
     * Un lien invisible ne porte aucun texte : il n'y aurait rien pour le lire, la ligne
     * elle-même n'étant pas dessinée.
     *
     * @return toujours la chaîne vide.
     */
    @Override
    public String label() {
        return "";
    }
}
