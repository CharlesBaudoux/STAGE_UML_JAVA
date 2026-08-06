package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Met en avant un attribut en lien entre deux Class du diagramme.
//
// Règle UML : un attribut ne doit jamais s'afficher en double sur le diagramme.
// Il s'écrit SOIT comme un texte à l'intérieur de la boîte, SOIT comme une flèche reliant deux
// boîtes.
// C'est notation() qui tranche, et l'outil se charge de tenir la règle : sous ARROW — le défaut —
// l'attribut est retiré de la boîte au profit du trait ; sous ATTRIBUTE il y reste, avec sa
// multiplicité à côté du nom, et aucun trait n'est dessiné.
//
// FIELD : l'annotation se pose sur un attribut ; c'est lui qui porte le type de la cible.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface UMLAssociation {

    // Classe située à l'autre bout du trait.
    // Volontairement sans default : un élément d'annotation sans valeur par défaut est
    // obligatoire, et c'est javac lui-même qui refuse une @UMLAssociation où la cible manque.
    // Rien n'est jamais déduit du type de l'attribut : ce qui est dessiné est ce qui est écrit.
    Class<?> target();

    // Comment le lien se dessine : un trait entre les deux boîtes, ou une ligne dans celle de
    // départ. Voir UMLNotation, qui décrit les deux notations.
    //
    // ARROW par défaut : c'est la forme qui montre le lien, et celle qu'on attend d'une
    // annotation dont c'est le nom.
    //
    // ATTRIBUTE ne garde que ce qu'une ligne d'attribut sait porter : la cible, qui en donne le
    // type, targetCardinality, qui en donne la multiplicité, et targetRole, qui devient le nom
    // même de la propriété — en notation attribut, c'est le rôle que l'on lit. Les paramètres
    // qui décorent un trait — type, direction, label, qualifier, le rôle et la multiplicité
    // côté source — n'ont alors nulle part où s'écrire ; le processeur d'annotations le signale
    // plutôt que de les laisser disparaître en silence.
    UMLNotation notation() default UMLNotation.ARROW;

    // Nature du lien : simple association, composition (losange plein) ou agrégation (creux).
    UMLRelationType type() default UMLRelationType.ASSOCIATION;

    // Multiplicités affichées à chaque extrémité, par exemple "1", "0..1", "*", "1..*".
    // Vide = rien d'affiché de ce côté.
    String sourceCardinality() default "";

    String targetCardinality() default "";

    // Rôles : le nom que joue chaque extrémité, écrit le long du trait. Vide = rien d'affiché.
    String sourceRole() default "";

    String targetRole() default "";

    // true ajoute la contrainte UML {ordered} : l'ordre des éléments est signifiant.
    boolean isOrdered() default false;

    // Qualificateur UML : la donnée par laquelle on désigne un objet à l'autre bout, par exemple
    // le numéro qui identifie une commande au sein d'un client. UML le dessine dans un petit carré
    // accolé à la source ; PlantUML ne sachant pas le faire, il est écrit entre crochets sur
    // l'extrémité du trait.
    String qualifier() default "";

    // Texte posé au milieu du trait, pour nommer l'association elle-même.
    String label() default "";

    // Contrainte de mise en page : force PlantUML à placer la cible en haut, en bas, à gauche ou
    // à droite. NONE laisse l'outil décider.
    UMLDirection direction() default UMLDirection.NONE;
}
