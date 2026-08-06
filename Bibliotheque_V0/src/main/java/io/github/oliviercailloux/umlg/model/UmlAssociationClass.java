package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Le rattachement d'une classe au lien qu'elle qualifie : « (Etudiant, Cours) .. Inscription ».
//
// La seule relation à trois boîtes du modèle. Les deux premières sont les extrémités du lien,
// la troisième est celle qui porte la relation — c'est-à-dire la classe d'association
// elle-même, qui n'a donc pas à être nommée ici : le rendu la connaît, puisque c'est elle qui
// déclare la relation.
//
// targetQualifiedName() rend l'extrémité d'arrivée, comme pour toute UmlRelation ; l'extrémité
// de départ s'ajoute à côté. Les deux sont validées de la même façon : ce sont des boîtes du
// diagramme, et le lien serait tracé depuis ou vers le vide sans elles.
public record UmlAssociationClass(String sourceQualifiedName, String targetQualifiedName)
    implements UmlRelation {

    public UmlAssociationClass {
        checkNotNull(sourceQualifiedName, "The source qualified name cannot be null.");
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");

        checkArgument(!sourceQualifiedName.isBlank(),
            "The source qualified name cannot be empty.");
        checkArgument(!targetQualifiedName.isBlank(),
            "The target qualified name cannot be empty.");
    }

    // Le trait en pointillés qui rejoint le lien ne porte pas d'étiquette : ce qu'il y aurait à
    // dire est écrit dans la boîte de la classe d'association, qui est là pour cela.
    @Override
    public String label() {
        return "";
    }
}
