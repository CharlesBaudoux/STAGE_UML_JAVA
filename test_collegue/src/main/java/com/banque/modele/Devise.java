package com.banque.modele;

import io.github.oliviercailloux.umlg.annotation.*;

/**
 * Couvre : le filtre anti-bruit sur un {@code record}, et @UMLNote sans direction — NONE, que
 * le rendu traduit par « à droite ».
 *
 * <p>Les accesseurs canoniques d'un record ({@code code()}, {@code symbole()}) jouent le rôle
 * de getters sans en porter le préfixe, puisque javac les nomme d'après le composant. Ils sont
 * néanmoins écartés, tout comme {@code equals}, {@code hashCode} et {@code toString} que le
 * compilateur ajoute. Seule {@code convertir} subsiste.
 *
 * <p>Un record n'a pas de mot-clé propre en UML : la boîte s'ouvre sur « class », ce qui décrit
 * exactement ce qu'il est — une classe finale.
 */
@UMLClass(strategy = UMLVisibility.PRIVATE)
@UMLNote(text = "Un record est dessiné comme une classe :\nUML ne lui réserve aucune notation.")
public record Devise(String code, String symbole) {

    public double convertir(double montant, Devise vers) {
        return montant;
    }
}
