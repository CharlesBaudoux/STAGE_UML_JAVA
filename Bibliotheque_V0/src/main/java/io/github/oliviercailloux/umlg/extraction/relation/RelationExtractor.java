package io.github.oliviercailloux.umlg.extraction.relation;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlRelation;

// Le contrat commun aux extracteurs de relations : « donne-moi les relations que porte cette
// classe ». C'est ce qui permet à EntityExtractor de les tenir dans une simple liste et de les
// appeler en boucle, sans savoir combien il y en a ni ce qu'ils cherchent.
public interface RelationExtractor {

    ImmutableList<? extends UmlRelation> extraire(Class<?> clazz);
}
