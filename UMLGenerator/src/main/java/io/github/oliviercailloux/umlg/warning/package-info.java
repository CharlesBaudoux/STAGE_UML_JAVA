// Le mécanisme des avertissements de la génération, tenu hors de la surface publique.
//
// Une seule classe, Avertisseur, et une seule raison d'être un paquetage à elle seule : il n'est
// pas exporté. « dire » est le geste de l'extraction et du rendu, jamais celui de l'utilisateur ;
// tant qu'elle vivait dans le paquetage exporté, aux côtés de la façade, rien n'empêchait un
// appelant d'injecter ses propres messages dans le compte rendu de la bibliothèque.
//
// La façade, elle, garde ce qui regarde l'appelant : Avertissements.versLeCanal, qui détourne les
// avertissements d'un fil vers le log d'un plugin Maven, et le Canal qu'il rend.
package io.github.oliviercailloux.umlg.warning;
