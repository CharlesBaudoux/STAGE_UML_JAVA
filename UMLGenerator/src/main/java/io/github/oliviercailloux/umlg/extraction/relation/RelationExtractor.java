package io.github.oliviercailloux.umlg.extraction.relation;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlRelation;

// Le contrat commun aux extracteurs de relations : « donne-moi les relations que porte cette
// classe ». C'est ce qui permet à EntityExtractor de les tenir dans une simple liste et de les
// appeler en boucle, sans savoir combien il y en a ni ce qu'ils cherchent.
//
// LES RÈGLES DU PROJET DESCENDENT JUSQU'ICI
//
// Un extracteur qui relit les membres de la classe doit les parcourir dans l'ordre que le projet
// a demandé — « order declaration » vaut pour les traits comme pour les compartiments, sans quoi
// une même boîte montre des attributs rangés dans l'ordre du code et des liens rangés par
// alphabet. C'est le rôle de la surcharge à deux arguments, que l'appelant emploie dès qu'il
// tient les Regles.
//
// Les extracteurs que l'ordre ne concerne pas — l'héritage se lit dans la signature de la classe,
// les dépendances dans ses annotations — n'ont rien à redéfinir : le défaut ci-dessous les sert.
public interface RelationExtractor {

    ImmutableList<? extends UmlRelation> extraire(Class<?> clazz);

    // Le même travail, informé des règles du projet. Le défaut les ignore, ce qui est exact pour
    // un extracteur qui ne relit aucun membre.
    default ImmutableList<? extends UmlRelation> extraire(Class<?> clazz, Regles regles) {
        return extraire(clazz);
    }
}
