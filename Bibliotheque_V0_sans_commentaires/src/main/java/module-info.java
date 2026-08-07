// La bibliothèque comme unité d'encapsulation.
//
// Ce que ce fichier protège. Le code interne est découpé en sous-paquetages
// (extraction.relation, rendering.syntax…) pour rester lisible. Java n'accordant aucun privilège
// d'accès entre un paquetage et ses sous-paquetages, ce découpage oblige à déclarer public des
// classes qui collaborent — AliasRegistry, MemberExtractor, PlantUmlEscaper… — et qui n'ont
// pourtant aucune vocation à être appelées de l'extérieur.
//
// La liste des exports ci-dessous rétablit la frontière au bon endroit : l'unité
// d'encapsulation devient la bibliothèque entière, et non plus le paquetage. Un utilisateur qui
// écrirait « import …umlg.rendering.syntax.AliasRegistry; » obtient une erreur de compilation.
// Le mot-clé public redevient ce qu'il devrait être ici — « visible de mes collaborateurs » — et
// exports porte seul le sens « visible de tous ».
//
// La règle à tenir. Trois paquetages sont exportés, et ce sont exactement les trois que la
// façade met en jeu : le point d'entrée, le vocabulaire que l'utilisateur écrit dans son code,
// et le modèle qu'il peut inspecter. Ajouter un exports est un engagement de stabilité, jamais
// une commodité pour débloquer un accès.
module io.github.oliviercailloux.umlg {

    requires com.google.common;
    requires net.sourceforge.plantuml;
    requires java.compiler;

    exports io.github.oliviercailloux.umlg;

    exports io.github.oliviercailloux.umlg.annotation;

    exports io.github.oliviercailloux.umlg.model;

    provides javax.annotation.processing.Processor
        with io.github.oliviercailloux.umlg.processing.UmlgProcessor;

}
