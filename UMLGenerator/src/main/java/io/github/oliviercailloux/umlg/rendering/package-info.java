// Du modèle à l'image : la traduction en PlantUML.
//
// C'est le seul endroit qui connaisse PlantUML. En entrée, des objets du paquetage model ; en
// sortie, un fichier SVG ou PNG.
//
// Deux étages :
//   - ici, l'orchestration — PlantUmlWriter mène la manœuvre, PlantUmlSyntaxBuilder assemble
//     le texte ;
//   - dessous, les sous-paquetages element (les morceaux de diagramme : boîtes, traits) et
//     syntax (les règles du langage PlantUML : alias, échappement, couleurs, style).
//
// La responsabilité propre à cet étage. Le modèle laisse passer n'importe quel texte dans un
// nom ou un label ; c'est ici qu'il devient sûr, parce que c'est ici, et seulement ici, qu'on
// sait dans quelle position syntaxique il va atterrir — entre guillemets, dans un stéréotype,
// sur un trait. D'où plusieurs méthodes d'échappement plutôt qu'une seule.
package io.github.oliviercailloux.umlg.rendering;
