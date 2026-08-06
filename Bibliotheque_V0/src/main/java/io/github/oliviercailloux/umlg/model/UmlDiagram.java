package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLFlow;

// Ce qui vaut pour le diagramme entier : son titre, son sens de lecture, le sort de ses
// compartiments vides.
//
// C'est le seul objet du modèle qui n'appartienne à aucune boîte. Il vient de @UMLDiagram, posée
// sur un package-info, et le projet en porte toujours un — celui qu'on a déclaré, ou le défaut
// quand personne n'a rien dit.
//
// Ce que ce record ne contient pas mérite d'être dit : ni couleur, ni police, ni épaisseur de
// trait. L'habillage se choisit à l'appel du générateur, par UmlStyle, et non dans le code
// annoté — il peut changer d'une génération à l'autre sans qu'une ligne de code bouge.
public record UmlDiagram(String title, UMLFlow direction, boolean hideEmptyMembers) {

    // Ce que vaut un projet dont aucun paquetage ne porte @UMLDiagram : aucun titre, le sens de
    // lecture ordinaire, et les compartiments vides masqués.
    //
    // Ce dernier point est le défaut de l'annotation elle-même, et pour la même raison : la
    // stratégie par défaut de @UMLClass ne montre aucun membre, si bien que ne pas masquer
    // dessinerait des rectangles vides sous chaque nom du cas nominal.
    private static final UmlDiagram DEFAUT = new UmlDiagram("", UMLFlow.TOP_TO_BOTTOM, true);

    public UmlDiagram {
        checkNotNull(title, "The title cannot be null; use the empty string for no title.");
        checkNotNull(direction, "The direction cannot be null; use UMLFlow.TOP_TO_BOTTOM.");

        // La chaîne vide veut dire « pas de titre » ; une chaîne d'espaces, elle, est un oubli :
        // elle réserverait en tête du diagramme la place d'un titre que personne ne verrait.
        checkArgument(title.isEmpty() || !title.isBlank(),
            "The title cannot be made of whitespace only; leave it empty for no title.");
    }

    public static UmlDiagram defaut() {
        return DEFAUT;
    }

    // Vrai quand un titre a été déclaré. Lu par le rendu, qui n'écrit la ligne « title » que
    // dans ce cas : une ligne vide occuperait tout de même de la hauteur.
    public boolean aUnTitre() {
        return !title.isEmpty();
    }
}
