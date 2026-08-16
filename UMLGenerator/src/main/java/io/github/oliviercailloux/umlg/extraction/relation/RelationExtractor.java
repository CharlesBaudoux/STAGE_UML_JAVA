package io.github.oliviercailloux.umlg.extraction.relation;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlRelation;

public interface RelationExtractor {
    ImmutableList<? extends UmlRelation> extract(Class<?> clazz, Rules rules);

    default ImmutableList<? extends UmlRelation> extract(Class<?> clazz) {
        return extract(clazz, Rules.no());
    }
}
