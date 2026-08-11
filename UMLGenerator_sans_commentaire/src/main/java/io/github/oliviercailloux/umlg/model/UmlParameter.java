package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Un paramètre d'une méthode, tel qu'il s'écrit dans la signature : {@code name: String}.
 *
 * <p>Le nom est facultatif, et c'est le compilateur qui en décide. Java n'écrit les noms des
 * paramètres dans le {@code .class} que si le projet est compilé avec l'option
 * {@code -parameters} ; sans elle, la réflexion ne connaît que {@code arg0}, {@code arg1}…
 * Plutôt que d'afficher ces noms-là, qui n'apprennent rien et que personne n'a écrits, un
 * paramètre sans nom ne montre que son type.
 *
 * <p>D'où deux formes possibles sur le dessin, et une seule règle pour choisir : le nom paraît
 * s'il existe vraiment.
 *
 * <pre>
 * representations(name: String)   le projet est compilé avec -parameters
 * representations(String)         il ne l'est pas
 * </pre>
 *
 * @param name le nom écrit dans le code, ou la chaîne vide quand la compilation ne l'a pas
 *     conservé.
 * @param type nom simple du type, comme partout ailleurs dans le modèle. Ni nul ni vide.
 */
public record UmlParameter(
    String name,
    String type
) {

    /**
     * Vérifie les invariants du paramètre.
     *
     * @throws NullPointerException si le nom ou le type est nul ; l'absence de nom se dit avec la
     *     chaîne vide.
     * @throws IllegalArgumentException si le type est vide, ou si le nom n'est fait que
     *     d'espaces.
     */
    public UmlParameter {
        checkNotNull(name, "The parameter name cannot be null (use an empty string).");
        checkNotNull(type, "The parameter type cannot be null.");

        checkArgument(!type.isBlank(), "The parameter type cannot be empty.");
        checkArgument(name.isEmpty() || !name.isBlank(),
            "The parameter name cannot be made of whitespace only.");
    }

    /**
     * Le cas d'un projet compilé sans {@code -parameters} : le type, et rien d'autre.
     *
     * @param type le nom simple du type.
     * @return un paramètre sans nom, qui ne montrera que son type.
     */
    public static UmlParameter anonyme(String type) {
        return new UmlParameter("", type);
    }

    /**
     * Le paramètre porte-t-il un nom ?
     *
     * @return vrai si le nom a survécu à la compilation, et sera donc écrit dans la signature.
     */
    public boolean nomme() {
        return !name.isEmpty();
    }
}
