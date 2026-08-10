package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Combien d'objets une propriété tient, écrit entre crochets à côté de son nom.
 *
 * <p>- aliases: String [0..*]
 * - ucp: int [0..1]
 * + names(): String [0..*]
 *
 * <p>POURQUOI ELLE EXISTE
 *
 * <p>La multiplicité d'un lien entre deux classes du diagramme se déclare déjà sur
 * &#64;UMLAssociation, qui la porte à l'extrémité du trait ou à la suite de la ligne selon sa
 * notation. Mais toutes les propriétés multiples ne sont pas des liens : un ensemble de chaînes,
 * une liste d'entiers, un champ facultatif n'ont aucune classe à l'autre bout, et n'ont donc rien
 * à faire dans une association. Cette annotation dit le nombre, et rien d'autre.
 *
 * <p>CE QU'ELLE NE DÉDUIT PAS
 *
 * <p>Rien, comme partout ailleurs dans la bibliothèque. Un attribut de type List pourrait laisser
 * deviner « plusieurs », jamais si c'est « 0..* » ou « 1..* » — cela relève du métier, et le
 * deviner produirait un diagramme faux la moitié du temps. Un Optional, de même, ne dit pas si
 * l'absence est un cas normal ou un accident.
 *
 * <p>FIELD : la multiplicité de l'attribut. METHOD : celle de la valeur rendue — « names(): String
 * [0..*] » se lit « rend zéro, une ou plusieurs chaînes ».
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UMLMultiplicity {

    /**
     * La multiplicité, dans le vocabulaire d'UML : "1", "0..1", "*", "1..*", "2..5".
     *
     * <p>Sans nom de paramètre : le nom de l'annotation dit déjà ce que la valeur est, et
     * « @UMLMultiplicity("0..*") » se lit mieux que la même chose écrite deux fois. La grammaire
     * est celle des multiplicités de lien, et c'est la même règle qui la vérifie — il n'y en a
     * qu'une en UML, qu'elle se lise le long d'un trait ou à côté d'un nom.
     *
     * @return la multiplicité, dans le vocabulaire d'UML.
     */
    String value();
}
