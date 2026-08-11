package io.github.oliviercailloux.umlg.annotation;

/**
 * Stratégie de sélection des membres : jusqu'où descendre dans les visibilités Java pour
 * décider ce qui entre dans la boîte.
 *
 * <p>La règle exacte est écrite dans VisibilityPolicy.estVisible ; @UMLInclude et @UMLExclude
 * l'emportent toujours sur la stratégie, membre par membre.
 */
public enum UMLVisibility {

    /**
     * « Celle que le projet a choisie » : la stratégie déclarée par la ligne « strategy » du
     * fichier umlg.rules, ou NONE si le projet n'en déclare aucune.
     *
     * <p>C'est le défaut de @UMLClass, et cette valeur n'existe que pour lui. Sans elle, une classe
     * qui ne dit rien serait indiscernable d'une classe qui demande NONE : une annotation Java ne
     * permet pas de savoir, par réflexion, si une valeur a été écrite ou laissée à son défaut.
     * Or la différence compte, et c'est tout l'intérêt du réglage de projet — écrire
     * « strategy public » une fois, puis revenir à la boîte vide sur la classe où on la veut.
     *
     * <p>Elle est résolue par EntityExtractor, qui la remplace par la stratégie du projet avant que
     * quiconque ait à décider quoi que ce soit : le reste de la bibliothèque ne voit jamais que
     * des visibilités concrètes.
     */
    PROJECT,

    /**
     * Rien : la boîte reste vide, sauf @UMLInclude explicites. Écrite sur une classe, elle
     * l'emporte sur ce que le projet a choisi.
     */
    NONE,

    /**
     * Seulement public.
     */
    PUBLIC,

    /**
     * public et protected.
     */
    PROTECTED,

    /**
     * public et « package-private » (membre sans aucun modificateur de visibilité).
     * À noter : protected n'entre pas dans ce cas — c'est délibéré, et vérifié par
     * VisibilityPolicyTest.
     */
    PACKAGE,

    /**
     * Tout, y compris private.
     */
    PRIVATE
}
