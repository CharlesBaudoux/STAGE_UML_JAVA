package io.github.oliviercailloux.umlg.annotation;

// Les deux façons de dessiner une même association.
//
// UML en offre deux, et n'en autorise qu'une à la fois pour une propriété donnée : ou bien un
// trait entre deux boîtes, ou bien une ligne dans la boîte de départ. Les deux disent exactement
// la même chose — une classe tient un ou plusieurs objets d'une autre — et le choix entre elles
// est affaire de lisibilité, non de sémantique.
//
// Le trait montre le lien ; il attire l'œil, et c'est ce qu'on veut pour les associations qui
// structurent le domaine. La ligne dans la boîte le range ; elle évite la toile d'araignée quand
// une classe en cite dix autres, ou quand la cible n'a rien d'intéressant à montrer d'elle-même.
public enum UMLNotation {

    // Un trait entre les deux boîtes, décoré à ses extrémités (voir RelationRenderer).
    // C'est le défaut : une association demandée sans rien préciser est un lien qui se voit.
    ARROW,

    // Une ligne du compartiment des attributs, de la forme « - Commande commandes [0..*] » : le
    // type est la classe visée, et la multiplicité cible se lit entre crochets à côté du nom.
    // Aucun trait n'est alors dessiné vers la cible.
    ATTRIBUTE
}
