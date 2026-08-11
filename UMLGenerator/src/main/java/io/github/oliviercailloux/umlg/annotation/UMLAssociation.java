package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Met en avant un attribut en lien entre deux Class du diagramme.
 *
 * <p>Règle UML : un attribut ne doit jamais s'afficher en double sur le diagramme.
 * Il s'écrit SOIT comme un texte à l'intérieur de la boîte, SOIT comme une flèche reliant deux
 * boîtes.
 * C'est notation() qui tranche, et l'outil se charge de tenir la règle : sous ARROW — le défaut —
 * l'attribut est retiré de la boîte au profit du trait ; sous ATTRIBUTE il y reste, avec sa
 * multiplicité à côté du nom, et aucun trait n'est dessiné.
 *
 * <p>FIELD : l'annotation se pose sur un attribut ; c'est lui qui porte le type de la cible.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface UMLAssociation {

    /**
     * Classe située à l'autre bout du trait.
     *
     * <p>Facultative depuis que la bibliothèque sait lire les paramètres de type : un attribut
     * déclaré « List&lt;Commande&gt; » dit déjà quelle classe est visée, et le répéter dans
     * l'annotation ne ferait qu'ouvrir un écart possible entre les deux. Elle reste nécessaire
     * là où le type ne dit rien — un attribut de type brut, une Map dont on ne saurait pas
     * laquelle des deux moitiés est la cible, un lien vers une classe qui n'apparaît pas dans
     * le type.
     *
     * <p>Ce n'est pas une entorse au principe « rien n'est deviné » : lire « List&lt;Commande&gt; » n'est
     * pas deviner, c'est lire ce que l'auteur a écrit, à l'endroit où il l'a écrit. Ce qui reste
     * indéductible — la multiplicité, qui relève du métier — reste déclaré.
     *
     * <p>void.class est la sentinelle « rien d'écrit » : une annotation Java ne peut pas avoir null
     * pour défaut, et void n'a jamais pu être une extrémité de lien.
     *
     * @return la classe visée, ou {@code void.class} pour la déduire du type de l'attribut.
     */
    Class<?> target() default void.class;

    /**
     * Comment le lien se dessine : un trait entre les deux boîtes, ou une ligne dans celle de
     * départ. Voir UMLNotation, qui décrit les deux notations.
     *
     * <p>ARROW par défaut : c'est la forme qui montre le lien, et celle qu'on attend d'une
     * annotation dont c'est le nom.
     *
     * <p>ATTRIBUTE ne garde que ce qu'une ligne d'attribut sait porter : la cible, qui en donne le
     * type, targetCardinality, qui en donne la multiplicité, et targetRole, qui devient le nom
     * même de la propriété — en notation attribut, c'est le rôle que l'on lit. Les paramètres
     * qui décorent un trait — type, direction, label, le rôle et la multiplicité côté source —
     * n'ont alors nulle part où s'écrire ; le processeur d'annotations le signale plutôt que de
     * les laisser disparaître en silence.
     *
     * @return la notation retenue.
     */
    UMLNotation notation() default UMLNotation.ARROW;

    /**
     * Nature du lien : simple association, composition (losange plein) ou agrégation (creux).
     *
     * @return la nature du lien.
     */
    UMLRelationType type() default UMLRelationType.ASSOCIATION;

    /**
     * Multiplicité affichée du côté de la source, par exemple {@code 1}, {@code 0..1},
     * {@code *}, {@code 1..*}.
     *
     * @return la multiplicité côté source ; vide = rien d'affiché de ce côté.
     */
    String sourceCardinality() default "";

    /**
     * Multiplicité affichée du côté de la cible, écrite comme celle de la source.
     *
     * @return la multiplicité côté cible ; vide = rien d'affiché de ce côté.
     */
    String targetCardinality() default "";

    /**
     * Rôle : le nom que joue l'extrémité de départ, écrit le long du trait.
     *
     * @return le rôle côté source ; vide = rien d'affiché.
     */
    String sourceRole() default "";

    /**
     * Rôle de l'extrémité d'arrivée. En notation attribut, c'est lui qui devient le nom même de
     * la propriété affichée dans la boîte.
     *
     * @return le rôle côté cible ; vide = rien d'affiché.
     */
    String targetRole() default "";

    /**
     * Texte posé au milieu du trait, pour nommer l'association elle-même.
     *
     * @return le texte du trait ; vide = rien d'affiché.
     */
    String label() default "";

    /**
     * Contrainte de mise en page : force PlantUML à placer la cible en haut, en bas, à gauche ou
     * à droite. NONE laisse l'outil décider.
     *
     * @return la contrainte de placement.
     */
    UMLDirection direction() default UMLDirection.NONE;
}
