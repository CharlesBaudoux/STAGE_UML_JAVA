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
// alphabet. Les règles font donc partie de la question posée, et c'est la version à deux
// arguments qui est le contrat : c'est elle qu'on implémente, et elle seule qu'EntityExtractor
// appelle.
//
// Les extracteurs que l'ordre ne concerne pas — l'héritage se lit dans la signature de la classe,
// les dépendances dans ses annotations — reçoivent des Regles dont ils n'ont rien à faire, et
// c'est très bien ainsi : ne pas les lire est une décision de l'extracteur, pas une lacune du
// contrat. L'inverse — la version à un argument en méthode principale — obligeait chaque
// extracteur sensible à l'ordre à redéfinir la surcharge et à laisser derrière lui une version
// sans règles qui donnait un autre résultat.
public interface RelationExtractor {

    ImmutableList<? extends UmlRelation> extraire(Class<?> clazz, Regles regles);

    // Le même travail, sans aucune règle de projet : le cas d'un appel direct, dans un test ou
    // dans un outil qui n'a pas de fichier umlg.json à donner.
    default ImmutableList<? extends UmlRelation> extraire(Class<?> clazz) {
        return extraire(clazz, Regles.aucune());
    }
}
