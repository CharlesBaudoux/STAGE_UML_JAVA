// Du code annoté au modèle : la lecture par réflexion.
//
// C'est le seul endroit de la bibliothèque qui connaisse java.lang.reflect. En sortie, il n'y a
// plus que des objets du paquetage model — ni Class, ni Field, ni Method ne franchissent la
// frontière.
//
// L'enchaînement : ProjectScanner trouve les classes annotées, EntityExtractor traduit chacune
// d'elles, en déléguant les membres au sous-paquetage member et les relations au sous-paquetage
// relation.
//
// Sur les erreurs. Le modèle sait dire « cette multiplicité est invalide » ; lui seul sait dire
// « sur l'attribut Client.commandes ». Les extracteurs enveloppent donc les échecs du modèle
// pour ajouter le « où » au « quoi » — d'où les try/catch qui reconstruisent le message autour
// d'une variable nommée « contexte ».
package io.github.oliviercailloux.umlg.extraction;
