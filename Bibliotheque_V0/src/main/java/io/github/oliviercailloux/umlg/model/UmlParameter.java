package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Un paramètre d'une méthode, tel qu'il s'écrit dans la signature : « name: String ».
//
// Le nom est facultatif, et c'est le compilateur qui en décide. Java n'écrit les noms des
// paramètres dans le .class que si le projet est compilé avec l'option « -parameters » ; sans
// elle, la réflexion ne connaît que « arg0 », « arg1 »… Plutôt que d'afficher ces noms-là, qui
// n'apprennent rien et que personne n'a écrits, un paramètre sans nom ne montre que son type.
//
// D'où deux formes possibles sur le dessin, et une seule règle pour choisir : le nom paraît s'il
// existe vraiment.
//   representations(name: String)   le projet est compilé avec -parameters
//   representations(String)         il ne l'est pas
public record UmlParameter(
    // Le nom écrit dans le code, ou la chaîne vide quand la compilation ne l'a pas conservé.
    String name,
    // Nom simple du type, comme partout ailleurs dans le modèle.
    String type
) {

    public UmlParameter {
        checkNotNull(name, "The parameter name cannot be null (use an empty string).");
        checkNotNull(type, "The parameter type cannot be null.");

        checkArgument(!type.isBlank(), "The parameter type cannot be empty.");
        checkArgument(name.isEmpty() || !name.isBlank(),
            "The parameter name cannot be made of whitespace only.");
    }

    // Le cas d'un projet compilé sans « -parameters » : le type, et rien d'autre.
    public static UmlParameter anonyme(String type) {
        return new UmlParameter("", type);
    }

    public boolean nomme() {
        return !name.isEmpty();
    }
}
