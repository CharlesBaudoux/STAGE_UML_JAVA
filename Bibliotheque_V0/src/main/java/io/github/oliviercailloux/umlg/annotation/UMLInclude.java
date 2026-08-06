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
// Les deux dérogations sur le même membre sont une erreur de compilation : l'intention est
// contradictoire, et le processeur refuse de choisir à la place de l'auteur (VerificateurMembre).
// La règle de départage existe pourtant — @UMLExclude l'emporte, comme partout (VisibilityPolicy)
// — mais elle n'est là que pour le code déjà compilé sans le processeur : rien de ce que
// l'extraction rencontre ne doit rester sans réponse.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UMLInclude {
}
