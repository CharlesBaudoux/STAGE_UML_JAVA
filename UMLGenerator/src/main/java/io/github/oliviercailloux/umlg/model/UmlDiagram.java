package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLFlow;

/**
 * Ce qui vaut pour le diagramme entier : son titre, son sens de lecture, le sort de ses
 * compartiments vides.
 *
 * <p>C'est le seul objet du modèle qui n'appartienne à aucune boîte. Il vient des lignes
 * {@code title}, {@code direction} et {@code show empty members} du fichier de règles, et le
 * projet en porte toujours un — celui qu'on a déclaré, ou le {@linkplain #defaults() défaut} quand
 * personne n'a rien dit.
 *
 * <p>Ces trois réglages étaient autrefois portés par une annotation, {@code @UMLDiagram}, posée
 * sur un {@code package-info}. Elle a été retirée : ils ne disent rien d'une classe, ils valent
 * pour le dessin entier, et c'est exactement ce que le fichier de règles existe pour porter.
 *
 * <p>Ce que ce record ne contient pas mérite d'être dit : ni couleur, ni police, ni épaisseur de
 * trait. L'habillage se choisit à l'appel du générateur, et non dans le code annoté — il peut
 * changer d'une génération à l'autre sans qu'une ligne de code bouge.
 *
 * @param title le titre écrit en tête du dessin, ou la chaîne vide pour n'en pas mettre.
 * @param direction le sens de lecture d'ensemble.
 * @param hideEmptyMembers vrai pour masquer les compartiments qu'aucun membre ne remplit.
 */
public record UmlDiagram(String title, UMLFlow direction, boolean hideEmptyMembers) {

    // L'instance partagée que rend defaults(), documentée là-bas.
    private static final UmlDiagram DEFAUT = new UmlDiagram("", UMLFlow.TOP_TO_BOTTOM, true);

    /**
     * Vérifie les invariants des réglages d'ensemble.
     *
     * @throws NullPointerException si le titre ou la direction est nul ; l'absence de titre se
     *     dit avec la chaîne vide.
     * @throws IllegalArgumentException si le titre n'est fait que d'espaces : il réserverait en
     *     tête du diagramme la place d'un titre que personne ne verrait.
     */
    public UmlDiagram {
        checkNotNull(title, "The title cannot be null; use the empty string for no title.");
        checkNotNull(direction, "The direction cannot be null; use UMLFlow.TOP_TO_BOTTOM.");

        // La chaîne vide veut dire « pas de titre » ; une chaîne d'espaces, elle, est un oubli :
        // elle réserverait en tête du diagramme la place d'un titre que personne ne verrait.
        checkArgument(title.isEmpty() || !title.isBlank(),
            "The title cannot be made of whitespace only; leave it empty for no title.");
    }

    /**
     * Ce que vaut un projet dont le fichier de règles ne dit rien du dessin : aucun titre, le
     * sens de lecture ordinaire, et les compartiments vides masqués.
     *
     * <p>Ce dernier point est le défaut de l'annotation elle-même, et pour la même raison : la
     * stratégie par défaut de {@code @UMLClass} ne montre aucun membre, si bien que ne pas
     * masquer dessinerait des rectangles vides sous chaque nom du cas nominal.
     *
     * @return les réglages par défaut, partagés.
     */
    public static UmlDiagram defaults() {
        return DEFAUT;
    }

    /**
     * Un titre a-t-il été déclaré ?
     *
     * @return vrai quand un titre a été déclaré. Lu par le rendu, qui n'écrit la ligne
     *     {@code title} que dans ce cas : une ligne vide occuperait tout de même de la hauteur.
     */
    public boolean hasTitle() {
        return !title.isEmpty();
    }
}
