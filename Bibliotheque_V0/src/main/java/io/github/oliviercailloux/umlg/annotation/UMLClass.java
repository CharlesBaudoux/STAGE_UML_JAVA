package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marque une classe comme faisant partie du diagramme.
//
// C'est l'annotation pivot : une classe qui ne la porte pas est ignorée par le scanner, et ne
// peut pas non plus servir de cible à une relation (voir TargetResolver.validerCible).
//
// RUNTIME : l'annotation doit survivre à la compilation, l'extraction la lit par réflexion.
// TYPE : elle se pose sur une classe, une interface, un enum ou un record.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLClass {

    // Nom affiché dans la boîte. Vide = on garde le nom simple de la classe Java.
    String name() default "";

    // Couleur de fond de la boîte, décodée par ColorCodec (nom PlantUML ou #RRGGBB).
    // Vide = pas de couleur imposée, PlantUML applique son thème.
    String color() default "";

    // Stéréotype UML affiché entre guillemets au-dessus du nom, par exemple « service ».
    // Vide = aucun stéréotype.
    String stereotype() default "";

    // Quels attributs et méthodes entrent dans la boîte, par défaut.
    // NONE : aucun ; seuls les membres explicitement marqués @UMLInclude apparaissent.
    UMLVisibility strategy() default UMLVisibility.NONE;
}
