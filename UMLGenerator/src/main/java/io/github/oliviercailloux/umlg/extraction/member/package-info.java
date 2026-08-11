// Ce qui remplit l'intérieur des boîtes : attributs et méthodes.
//
// MemberExtractor mène la boucle ; l'absence d'un membre du diagramme s'explique toujours par
// l'une de ces deux questions, tenues chacune à un endroit distinct :
//   - « le compilateur l'a-t-il fabriqué ? » (isSynthetic, dans la boucle même) ;
//   - « l'utilisateur veut-il le voir ? » (MemberExtractor.retenu, qui compose dans cet ordre
//     @UMLExclude, @UMLInclude, puis les deux défauts généraux — la stratégie de visibilité que
//     lit VisibilityPolicy, et les motifs du fichier de règles, « "hide": ["get*", "log"] »).
//
// La seconde question s'écrit une fois et une seule, et rend du même coup le nom sous lequel le
// membre paraît : les quatre compartiments — attributs, méthodes, méthodes en notation attribut,
// littéraux d'énumération — l'appellent, aucun ne la réécrit.
package io.github.oliviercailloux.umlg.extraction.member;
