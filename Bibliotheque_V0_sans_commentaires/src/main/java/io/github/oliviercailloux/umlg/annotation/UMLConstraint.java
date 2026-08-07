package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Attache une règle formelle à une classe, un attribut ou un lien.
//
// UML écrit une contrainte entre accolades — « {solde >= 0} », « {subset} », « {ordered} ». Ce
// sont les seules règles métier que le diagramme sache porter, et elles disent ce qu'aucune
// multiplicité ni aucun type ne peut exprimer.
//
// Les accolades sont ajoutées par le générateur : la règle s'écrit nue.
//
// TYPE et FIELD, jamais METHOD : une contrainte porte sur un état, pas sur un appel.
//
// Sur un attribut porteur de @UMLAssociation, la contrainte s'applique au lien et non à
// l'attribut — c'est l'usage majoritaire, celui de {subset} et {ordered}.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
@Repeatable(UMLConstraints.class)
public @interface UMLConstraint {

    String rule();
}
