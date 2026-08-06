package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;

// Un lien invisible, posé pour contraindre le placement d'une boîte.
//
// C'est la seule relation du modèle qui ne se voie pas sur le dessin, et la seule qui ne dise
// rien du domaine. Elle est pourtant une relation à part entière : elle relie deux boîtes, se
// résout par les mêmes alias, et s'écrit sur une ligne du même fichier. La ranger ailleurs
// obligerait à dupliquer tout cela pour un cas unique.
//
// Record à deux composantes, donc equals() fourni : c'est ce qui permet à LayoutExtractor de
// repérer deux contraintes identiques avec un simple ensemble.
public record UmlLayout(String targetQualifiedName, UMLDirection direction)
    implements UmlRelation {

    public UmlLayout {
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(direction, "The direction cannot be null (use UMLDirection.NONE).");

        checkArgument(!targetQualifiedName.isBlank(),
            "The target qualified name cannot be empty.");
    }

    // Un lien invisible ne porte aucun texte : il n'y aurait rien pour le lire, la ligne
    // elle-même n'étant pas dessinée. La chaîne vide dit exactement cela, et RelationRenderer
    // n'écrit alors rien.
    @Override
    public String label() {
        return "";
    }
}
