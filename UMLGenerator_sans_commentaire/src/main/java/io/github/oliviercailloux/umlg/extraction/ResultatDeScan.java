package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlProject;

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
