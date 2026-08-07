package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marque un élément comme dérivé : il n'est pas stocké, il se calcule à partir d'autres données.
//
// UML note un tel élément d'une barre oblique — « /age » —, que Java ne permet pas d'écrire dans
// un identifiant. C'est tout ce que fait cette annotation : rendre disponible au rendu une
// information que le langage ne sait pas porter.
//
// FIELD et METHOD, jamais TYPE : la notion de classe dérivée n'existe pas en UML.
//
// Sur un attribut porteur de @UMLAssociation, c'est le lien qui est dit dérivé, et la barre se
// place devant le rôle cible — l'attribut, lui, n'est plus dans la boîte.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UMLDerived {
}
