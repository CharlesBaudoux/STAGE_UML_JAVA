package io.github.oliviercailloux.umlg.extraction.relation;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlRelation;

public interface RelationExtractor {

    ImmutableList<? extends UmlRelation> extraire(Class<?> clazz, Regles regles);

    default ImmutableList<? extends UmlRelation> extraire(Class<?> clazz) {
        return extraire(clazz, Regles.aucune());
    }
}
