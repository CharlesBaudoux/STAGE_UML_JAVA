// Les règles du langage PlantUML, isolées des formes qu'on y dessine.
//
// Quatre préoccupations, une classe chacune :
//   - AliasRegistry : nommer les boîtes par un identifiant que PlantUML accepte, et garantir
//     son unicité ;
//   - PlantUmlEscaper : rendre inoffensif un texte venu de l'utilisateur, selon la position
//     syntaxique où il atterrit ;
//   - ColorCodec : vérifier qu'une couleur existe vraiment dans le catalogue de PlantUML ;
//   - PlantUmlLayout : le bloc de style et le calcul des espacements.
//
// Aucune ne connaît le modèle : elles ne manipulent que des chaînes et des entiers. C'est ce
// qui les rend testables isolément, sans construire un diagramme.
package io.github.oliviercailloux.umlg.rendering.syntax;
