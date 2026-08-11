package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;

// Une vue déclarée dans le fichier de règles : son nom, ce qu'elle contient, son sens de lecture.
//
// CE QU'ELLE N'EST PAS
//
// Elle ne contient pas de classes, seulement la façon de les désigner. La résolution — passer des
// noms écrits aux boîtes du modèle — a lieu bien plus tard, quand le projet est connu ; c'est ce
// qui permet de refuser un nom introuvable en nommant la vue fautive.
//
// L'ORDRE COMPTE
//
// Les classes sont retenues dans l'ordre où elles sont écrites, et cet ordre devient celui du
// dessin. C'est délibéré : sur les diagrammes tenus à la main, l'ordre des boîtes est celui de
// l'exposé qui les accompagne — on présente d'abord ceci, puis cela. Cet ordre-là n'est écrit
// nulle part dans le code et aucun calcul ne peut le retrouver ; l'écrire ici est le seul moyen
// de le dire, et il ne coûte rien puisqu'il faut de toute façon énumérer les classes.
public record DefinitionDeVue(String nom, ImmutableList<Selecteur> retenus,
    ImmutableList<Selecteur> retires, UMLFlow direction) {

    public DefinitionDeVue {
        checkNotNull(nom, "The view name cannot be null.");
        checkNotNull(retenus, "The list of selectors cannot be null.");
        checkNotNull(retires, "The list of removed selectors cannot be null.");
        checkNotNull(direction, "The direction cannot be null; use UMLFlow.TOP_TO_BOTTOM.");

        checkArgument(!nom.isBlank(), "The view name cannot be empty or made of whitespace only.");
        checkArgument(!retenus.isEmpty(), "A view must retain at least one class.");
    }
}
