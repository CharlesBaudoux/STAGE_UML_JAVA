package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Le rattachement d'une classe au lien qu'elle qualifie :
 * {@code (Etudiant, Cours) .. Inscription}.
 *
 * <p>La seule relation à trois boîtes du modèle. Les deux premières sont les extrémités du lien,
 * la troisième est celle qui porte la relation — c'est-à-dire la classe d'association elle-même,
 * qui n'a donc pas à être nommée ici : le rendu la connaît, puisque c'est elle qui déclare la
 * relation.
 *
 * <p>{@link #targetQualifiedName()} rend l'extrémité d'arrivée, comme pour toute
 * {@link UmlRelation} ; l'extrémité de départ s'ajoute à côté. Les deux sont validées de la même
 * façon : ce sont des boîtes du diagramme, et le lien serait tracé depuis ou vers le vide sans
 * elles.
 *
 * @param sourceQualifiedName le nom complet de l'extrémité de départ du lien qualifié. Ni nul ni
 *     vide.
 * @param targetQualifiedName le nom complet de son extrémité d'arrivée. Ni nul ni vide.
 */
public record UmlAssociationClass(String sourceQualifiedName, String targetQualifiedName)
    implements UmlRelation {

    /**
     * Vérifie que les deux extrémités sont nommées.
     *
     * @throws NullPointerException si l'un des deux noms est nul.
     * @throws IllegalArgumentException si l'un des deux noms est vide.
     */
    public UmlAssociationClass {
        checkNotNull(sourceQualifiedName, "The source qualified name cannot be null.");
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");

        checkArgument(!sourceQualifiedName.isBlank(),
            "The source qualified name cannot be empty.");
        checkArgument(!targetQualifiedName.isBlank(),
            "The target qualified name cannot be empty.");
    }

    /**
     * Le trait en pointillés qui rejoint le lien ne porte pas d'étiquette : ce qu'il y aurait à
     * dire est écrit dans la boîte de la classe d'association, qui est là pour cela.
     *
     * @return toujours la chaîne vide.
     */
    @Override
    public String label() {
        return "";
    }
}
