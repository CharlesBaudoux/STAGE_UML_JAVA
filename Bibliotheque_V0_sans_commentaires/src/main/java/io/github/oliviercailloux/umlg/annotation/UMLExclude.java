package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Cache un membre que la stratégie de la classe ferait entrer dans la boîte.
//
// C'est le veto de plus haute priorité : il l'emporte sur la stratégie comme sur @UMLInclude.
// Posée sur un attribut annoté @UMLAssociation, elle supprime aussi le lien correspondant
// (voir AssociationExtractor).
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UMLExclude {
}
