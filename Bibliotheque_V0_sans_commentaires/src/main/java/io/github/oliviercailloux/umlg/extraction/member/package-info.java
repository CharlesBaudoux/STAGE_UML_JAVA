// Ce qui remplit l'intérieur des boîtes : attributs et méthodes.
//
// MemberExtractor mène la boucle ; il s'appuie sur deux décideurs séparés, chacun tenant une
// question distincte :
//   - NoiseFilter : « cette méthode apprend-elle quelque chose ? » (getters, setters, equals,
//     accesseurs de record, méthodes engendrées des enums) ;
//   - VisibilityPolicy : « l'utilisateur veut-il la voir ? » (stratégie de visibilité,
//     @UMLInclude, @UMLExclude).
//
// Les séparer permet de les tester isolément, et de comprendre l'absence d'un membre du
// diagramme en regardant un seul des deux.
package io.github.oliviercailloux.umlg.extraction.member;
