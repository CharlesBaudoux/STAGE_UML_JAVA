package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Déclare une dépendance : « cette classe se sert de cette autre », sans qu'un attribut ne la
 * détienne. Dessinée en pointillés fléchés. Typiquement : un paramètre de méthode, un type de
 * retour, une classe utilitaire appelée en statique.
 *
 * <p>L'annotation se pose sur la classe (et non sur un membre) parce qu'aucun élément précis du
 * code ne porte la relation : c'est la classe entière qui dépend de l'autre.
 *
 * <p>&#64;Repeatable : on peut en poser plusieurs sur la même classe ; le compilateur les regroupe
 * alors automatiquement dans le conteneur @UMLDependencies.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(UMLDependencies.class)
public @interface UMLDependency {

    /**
     * Classe dont on dépend. Obligatoire (pas de valeur par défaut) : contrairement à une
     * association, aucun type d'attribut ne permettrait de la deviner.
     *
     * <p>Le nom « target » est celui qu'emploient toutes les annotations désignant l'autre bout
     * d'un lien : une convention uniforme évite d'avoir à se souvenir laquelle dit quoi.
     *
     * @return la classe dont on dépend.
     */
    Class<?> target();

    /**
     * Texte posé sur la flèche, par exemple {@code utilise} ou {@code crée}.
     *
     * @return le texte de la flèche ; vide = flèche nue.
     */
    String label() default "";
}
