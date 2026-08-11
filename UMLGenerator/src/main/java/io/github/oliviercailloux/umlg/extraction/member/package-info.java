// Ce qui remplit l'intérieur des boîtes : attributs et méthodes.
//
// MemberExtractor mène la boucle ; l'absence d'un membre du diagramme s'explique toujours par
// l'une de ces trois questions, tenues chacune à un endroit distinct :
//   - « le compilateur l'a-t-il fabriqué ? » (isSynthetic, dans la boucle même) ;
//   - « le projet le tient-il pour du bruit ? » (Regles.membreMasque, les motifs du fichier de
//     règles — « "hide": ["get*", "log"] ») ;
//   - « l'utilisateur veut-il le voir ? » (VisibilityPolicy : stratégie de visibilité,
//     @UMLInclude, @UMLExclude).
package io.github.oliviercailloux.umlg.extraction.member;
