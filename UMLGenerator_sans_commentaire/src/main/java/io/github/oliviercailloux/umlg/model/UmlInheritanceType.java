package io.github.oliviercailloux.umlg.model;

/**
 * Les deux natures de lien d'héritage.
 *
 * <p>Toutes deux portent le même triangle creux du côté du parent ; seul le trait diffère.
 */
public enum UmlInheritanceType {

    /**
     * Un {@code extends} entre deux classes, ou entre deux interfaces : la source est une sorte
     * de la cible, dont elle hérite la structure. Trait plein.
     */
    GENERALIZATION,

    /**
     * Un {@code implements} : la source s'engage à honorer le contrat de la cible, sans rien
     * hériter de sa structure. Trait pointillé, cette différence de dessin étant justement là
     * pour dire que le lien est d'une autre nature.
     */
    REALIZATION
}
