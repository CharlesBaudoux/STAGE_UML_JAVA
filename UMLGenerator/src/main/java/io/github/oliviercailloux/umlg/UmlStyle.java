package io.github.oliviercailloux.umlg;

/**
 * L'apparence du diagramme : qui, de PlantUML ou de la bibliothèque, décide des couleurs.
 *
 * <p>Cette énumération ne change jamais ce que le diagramme dit — les mêmes boîtes, les mêmes
 * traits, les mêmes membres. Elle ne porte que sur son habillage.
 *
 * <p>POURQUOI ELLE EXISTE
 *
 * <p>La bibliothèque imposait autrefois son thème à tout le monde, sans qu'aucun réglage ne
 * permette de s'en écarter. Or l'apparence fait partie du diagramme que l'utilisateur veut :
 * lui refuser ce choix, c'est décider à sa place. Les deux valeurs ci-dessous sont donc offertes
 * à égalité, et la première est le défaut parce qu'elle est la moins présomptueuse — ne rien
 * imposer est un meilleur point de départ qu'imposer nos goûts.
 *
 * <p>CE QUI RESTE COMMUN AUX DEUX
 *
 * <p>Deux réglages échappent au choix, parce qu'ils touchent au sens et non au goût :
 * - classAttributeIconSize 0 : les visibilités s'écrivent « + », « - », « # », « ~ », la
 * notation d'UML, plutôt qu'en pastilles colorées propres à PlantUML ;
 * - l'espacement, calculé sur la plus longue étiquette du diagramme (voir PlantUmlLayout),
 * sans quoi les étiquettes de lien se chevauchent.
 *
 * <p>Un troisième leur ressemble sans leur appartenir : le masquage des compartiments vides, que
 * les deux apparences écrivent pareillement, mais que la ligne « show empty members » du fichier
 * de règles peut lever. Ce n'est donc pas un invariant du rendu, c'est un réglage du diagramme
 * sur lequel le style n'a pas d'avis — la nuance vit dans PlantUmlLayout, qui le pose hors du
 * bloc commun.
 */
public enum UmlStyle {

    /**
     * Ce que PlantUML dessine quand on ne lui dit rien : boîtes grises, pastille « C » verte ou
     * « I » violette accolée au nom, traits et flèches par défaut.
     *
     * <p>C'est le défaut de la bibliothèque. Un utilisateur qui n'a rien demandé obtient le rendu
     * habituel de PlantUML, celui qu'il reconnaîtra de tous les diagrammes qu'il a déjà vus, et
     * celui auquel ressemblent les diagrammes écrits à la main.
     */
    PLANTUML,

    /**
     * Le thème propre à la bibliothèque : bandeau bleu pâle sur fond blanc cassé, bords bleu
     * foncé, coins arrondis, pas d'ombre, nom des liens en italique coloré, et pastille masquée
     * — le mot-clé « interface » ou « abstract class » écrit dans la boîte dit déjà la même
     * chose.
     *
     * <p>Plus sobre à l'impression et sur un fond clair, au prix d'un repère visuel en moins.
     */
    UMLG
}
