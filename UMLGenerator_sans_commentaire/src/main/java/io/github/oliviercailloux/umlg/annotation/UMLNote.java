package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attache une bulle de commentaire à une classe, un attribut ou une méthode.
 *
 * <p>Le diagramme dit le « comment » — la structure ; la note dit le « pourquoi ». C'est le seul
 * endroit où une phrase en langue naturelle a sa place sur le dessin, et cela suffit à justifier
 * qu'elle existe : un algorithme délicat ou une dette assumée ne se lisent dans aucune boîte.
 *
 * <p>&#64;Repeatable : plusieurs notes peuvent se poser sur le même élément.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Repeatable(UMLNotes.class)
public @interface UMLNote {

    /**
     * Le contenu de la bulle. Obligatoire, et non vide : une note sans texte n'est qu'un carré
     * jaune posé au hasard.
     *
     * @return le texte de la bulle. Obligatoire et non vide.
     */
    String text();

    /**
     * De quel côté de l'élément la bulle se place. NONE laisse la bibliothèque choisir, et elle
     * choisit la droite — le placement d'usage, et celui qui dérange le moins la lecture
     * verticale de l'héritage.
     *
     * @return le côté où poser la bulle.
     */
    UMLDirection direction() default UMLDirection.NONE;
}
