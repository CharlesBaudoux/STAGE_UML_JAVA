package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Range la classe dans un groupe nommé, dessiné comme un rectangle qui l'entoure.
 *
 * <p>Sur un diagramme de quarante classes, le lecteur ne sait plus où regarder. Les rassembler par
 * domaine — « Sécurité », « Facturation », « Catalogue » — divise la complexité visuelle sans
 * rien changer au modèle : les mêmes boîtes, les mêmes traits, simplement rangés.
 *
 * <p>LE GROUPE N'EST PAS LE PAQUETAGE JAVA
 *
 * <p>Le groupe est déclaré, jamais déduit du paquetage où la classe se trouve. C'est délibéré : le
 * découpage qui fait un bon diagramme n'est pas toujours celui qui fait une bonne arborescence
 * de fichiers, et l'un ne doit pas obliger à refaire l'autre. Deux classes de paquetages
 * différents peuvent ainsi figurer dans le même rectangle, et inversement.
 *
 * <p>Le groupe est identifié par son nom : deux classes qui déclarent le même name() entrent dans
 * le même rectangle. Si elles déclarent des couleurs différentes, la première rencontrée
 * l'emporte et un avertissement est émis — le rectangle n'en a qu'une.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLPackage {

    /**
     * Le nom du groupe, écrit sur le rectangle. Sans valeur par défaut, donc obligatoire : un
     * groupe anonyme ne dirait pas ce qu'il rassemble, et c'est tout ce qu'on lui demande.
     *
     * @return le nom du groupe. Obligatoire.
     */
    String name();

    /**
     * Couleur de fond du rectangle, décodée par ColorCodec comme celle d'une boîte (nom PlantUML
     * ou #RRGGBB).
     *
     * @return la couleur du rectangle ; vide = pas de couleur imposée.
     */
    String color() default "";
}
