// Les règles qu'un projet applique à tous ses diagrammes, déclarées dans un fichier plutôt que
// sur chaque élément.
//
// Cinq classes. Deux portent le métier : Regles tient ce qui a été demandé et répond aux questions
// de l'extraction, LecteurDeRegles traduit l'objet JSON d'un fichier écrit à la main en un jeu de
// Regles, et refuse ce qu'il ne comprend pas. Les trois autres sont ce que le lecteur produit et
// que Regles conserve — Motif, une famille de noms de membres ; Selecteur, une façon de désigner
// des classes ; DefinitionDeVue, une vue telle que le fichier l'écrit.
//
// C'est le paquetage où viendront les natures de règles à venir. Ce qu'on masque partout et ce
// qu'on abrège y sont déjà — « hide » et « types » —, ce qu'on place aussi, pour partie :
// « direction » dit le sens de lecture, mais rien ne dit encore où une boîte se pose. Voir Regles
// pour la forme du fichier.
package io.github.oliviercailloux.umlg.extraction.rules;
