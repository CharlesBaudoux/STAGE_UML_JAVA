package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Le groupe visuel auquel une boîte appartient : un rectangle nommé qui l'entoure.
//
// Ce n'est pas le paquetage Java de la classe, et le nom du record ne doit pas tromper : il suit
// celui de l'annotation, @UMLPackage, comme partout ailleurs dans le modèle. Le groupe est
// déclaré, jamais déduit — le découpage qui fait un bon diagramme n'est pas toujours celui qui
// fait une bonne arborescence de fichiers.
//
// L'identité du groupe est son nom : deux entités portant le même name() entrent dans le même
// rectangle, quelles que soient leurs classes d'origine.
public record UmlPackage(String name, String color) {

    private static final UmlPackage AUCUN = new UmlPackage("", "");

    public UmlPackage {
        checkNotNull(name, "The group name cannot be null (use an empty string for no group).");
        checkNotNull(color, "The group color cannot be null (use an empty string).");

        // La chaîne vide dit « aucun groupe » ; une chaîne d'espaces dessinerait un rectangle
        // dont l'étiquette serait invisible, ce qui n'est jamais ce qu'on veut.
        checkArgument(name.isEmpty() || !name.isBlank(),
            "The group name cannot be made of whitespace only.");
        checkArgument(color.isEmpty() || !color.isBlank(),
            "The group color cannot be made of whitespace only.");
        // Une couleur sans groupe ne serait posée sur rien.
        checkArgument(!name.isEmpty() || color.isEmpty(),
            "A group color was given without a group name; there would be nothing to paint.");
    }

    // Ce que porte une entité qu'aucune @UMLPackage ne range : elle se dessine au premier
    // niveau du diagramme, hors de tout rectangle.
    public static UmlPackage aucun() {
        return AUCUN;
    }

    public boolean estGroupee() {
        return !name.isEmpty();
    }
}
