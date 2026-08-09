package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque une classe comme faisant partie du diagramme.
 *
 * <p>C'est l'annotation pivot : une classe qui ne la porte pas est ignorée par le scanner, et ne
 * peut pas non plus servir de cible à une relation (voir TargetResolver.validerCible).
 *
 * <p>RUNTIME : l'annotation doit survivre à la compilation, l'extraction la lit par réflexion.
 * TYPE : elle se pose sur une classe, une interface, un enum ou un record.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLClass {

    /**
     * Nom affiché dans la boîte. Vide = on garde le nom simple de la classe Java.
     */
    String name() default "";

    /**
     * Couleur de fond de la boîte, décodée par ColorCodec (nom PlantUML ou #RRGGBB).
     * Vide = pas de couleur imposée, PlantUML applique son thème.
     */
    String color() default "";

    /**
     * Stéréotype UML affiché entre guillemets au-dessus du nom, par exemple « service ».
     * Vide = aucun stéréotype.
     */
    String stereotype() default "";

    /**
     * Quels attributs et méthodes entrent dans la boîte, par défaut.
     *
     * <p>PROJECT — le défaut — veut dire « celle que le projet a choisie », c'est-à-dire la ligne
     * « strategy » du fichier umlg.rules, ou NONE si le projet n'en déclare aucune. Écrire une
     * valeur ici ne vaut donc que pour cette classe, et l'emporte sur le réglage du projet.
     *
     * <p>NONE : aucun membre ; seuls ceux explicitement marqués @UMLInclude apparaissent. C'est ce
     * qu'on écrit pour ramener une classe à sa boîte nue dans un projet qui montre le reste.
     */
    UMLVisibility strategy() default UMLVisibility.PROJECT;
}
