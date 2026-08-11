package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlProject;

// Ce qu'un scan rend : le projet lu, et ce qu'il a laissé de côté.
//
// Les classes ignorées ne décrivent pas le diagramme — elles n'y sont justement pas — et n'ont
// donc rien à faire dans UmlProject. Ce raisonnement est juste ; il plaidait pour un type de
// retour distinct, et non pour le canal latéral qui tenait ici : une variable de fil, posée par
// scan() et lue juste après par un appel statique séparé.
//
// Ce que ce record répare. La variable n'était jamais retirée, donc retenue indéfiniment sur un
// fil réutilisé — celui de Maven, celui d'un serveur. Et un scan qui échouait avant de la poser
// laissait classesIgnorees(), qui était publique, rendre le compte du scan précédent : le
// couplage temporel « scanner d'abord, lire ensuite » n'était écrit nulle part, et rien
// n'empêchait de le violer.
//
// Un type de retour n'a pas d'ordre d'appel à respecter, donc pas d'ordre à violer.
public record ResultatDeScan(UmlProject projet, ImmutableList<String> classesIgnorees) {

    /**
     * Vérifie les invariants du résultat.
     *
     * @throws NullPointerException si le projet ou la liste est nul ; un scan sans classe
     *     ignorée se dit avec une liste vide.
     */
    public ResultatDeScan {
        checkNotNull(projet, "The scanned project cannot be null.");
        checkNotNull(classesIgnorees,
            "The list of ignored classes cannot be null (use an empty list).");
    }
}
