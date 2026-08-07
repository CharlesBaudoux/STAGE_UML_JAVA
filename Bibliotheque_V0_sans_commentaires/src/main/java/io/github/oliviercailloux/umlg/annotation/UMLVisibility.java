package io.github.oliviercailloux.umlg.annotation;

// Stratégie de sélection des membres : jusqu'où descendre dans les visibilités Java pour
// décider ce qui entre dans la boîte.
//
// La règle exacte est écrite dans VisibilityPolicy.estVisible ; @UMLInclude et @UMLExclude
// l'emportent toujours sur la stratégie, membre par membre.
public enum UMLVisibility {

    NONE,

    PUBLIC,

    PROTECTED,

    PACKAGE,

    PRIVATE
}
