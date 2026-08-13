package io.github.oliviercailloux.umlg.annotation;

/**
 * Un côté : celui où pousser l'élément placé, par rapport à l'élément qui le porte. C'est une
 * contrainte de mise en page, sans aucun sens UML.
 *
 * <p>Cet énuméré sert à deux endroits, et il faut savoir lequel on lit, car ce qui est placé n'est
 * pas le même objet :
 *
 * <ul>
 *   <li>{@link UMLAssociation#direction()} place <b>la cible du lien</b> par rapport à sa source.
 *       PlantUML en fait une contrainte de son moteur de placement : {@code A -down-> B} pousse B
 *       sous A. Les quatre côtés sont acceptés.
 *   <li>{@link UMLNote#direction()} place <b>la bulle</b> par rapport à l'élément qu'elle
 *       commente. Sur une note de classe, les quatre côtés sont acceptés. Sur une note d'attribut
 *       ou de méthode, PlantUML ne connaît que la gauche et la droite : {@code UP} et {@code DOWN}
 *       s'y rabattent sur la droite, et le processeur d'annotations le signale à la compilation
 *       plutôt que de le taire.
 * </ul>
 *
 * <p>À ne pas confondre avec {@link UMLFlow}, qui dit dans quel sens la flèche pointe, non où la
 * boîte se pose.
 */
public enum UMLDirection {

    /**
     * Aucune contrainte : PlantUML choisit seul. C'est le défaut, et le bon choix en général.
     *
     * <p>Une note fait exception, PlantUML exigeant un côté : la bibliothèque prend alors la
     * droite, le placement d'usage et celui qui dérange le moins la lecture verticale de
     * l'héritage.
     */
    NONE,

    /** Au-dessus : la cible du lien, ou la bulle, est poussée au-dessus de son porteur. */
    UP,

    /** En dessous : la cible du lien, ou la bulle, est poussée sous son porteur. */
    DOWN,

    /** À gauche : la cible du lien, ou la bulle, est poussée à gauche de son porteur. */
    LEFT,

    /** À droite : la cible du lien, ou la bulle, est poussée à droite de son porteur. */
    RIGHT
}
