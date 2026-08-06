package io.github.oliviercailloux.umlg.annotation;

// Stratégie de sélection des membres : jusqu'où descendre dans les visibilités Java pour
// décider ce qui entre dans la boîte.
//
// La règle exacte est écrite dans VisibilityPolicy.estVisible ; @UMLInclude et @UMLExclude
// l'emportent toujours sur la stratégie, membre par membre.
public enum UMLVisibility {

    // Rien par défaut : la boîte reste vide, sauf @UMLInclude explicites.
    NONE,

    // Seulement public.
    PUBLIC,

    // public et protected.
    PROTECTED,

    // public et « package-private » (membre sans aucun modificateur de visibilité).
    // À noter : protected n'entre pas dans ce cas — c'est délibéré, et vérifié par
    // VisibilityPolicyTest.
    PACKAGE,

    // Tout, y compris private.
    PRIVATE
}
