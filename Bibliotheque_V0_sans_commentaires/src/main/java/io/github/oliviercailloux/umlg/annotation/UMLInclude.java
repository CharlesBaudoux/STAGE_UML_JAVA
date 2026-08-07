package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Force l'affichage d'un membre, quelle que soit la stratégie de la classe.
//
// Sert dans deux cas : montrer un membre trop peu visible pour la stratégie choisie (un private
// avec strategy = PUBLIC), et rattraper une méthode écartée par NoiseFilter — un getter que
// l'on juge, exceptionnellement, digne du diagramme.
//
// @UMLExclude l'emporte si les deux sont posées sur le même membre.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UMLInclude {
}
