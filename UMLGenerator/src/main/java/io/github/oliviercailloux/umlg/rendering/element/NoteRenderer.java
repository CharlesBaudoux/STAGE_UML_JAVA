package io.github.oliviercailloux.umlg.rendering.element;

import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.model.UmlNote;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

// Écrit les bulles de commentaire.
//
// Forme produite, selon ce que la note vise :
//   note right of Client : Ne pas modifier sans relire le contrat
//   note left of Client::valider
//   &#65;lgorithme critique
//   end note
//
// Deux formes, parce que PlantUML n'en accepte qu'une dans chaque cas : la forme d'une seule
// ligne lui suffit pour une classe, mais il la refuse pour un membre, où seul le bloc fonctionne.
// Le fait a été vérifié plutôt que supposé.
//
// Les côtés disponibles diffèrent eux aussi, et pour la même raison — l'un a été vérifié, l'autre
// aussi : une note de classe se pose des quatre côtés, une note de membre à gauche ou à droite
// seulement. D'où deux traductions du même énuméré, placement() et placementSurMembre().
//
// Chacune porte donc la protection que sa position exige. La forme d'une seule ligne n'a rien à
// refermer : l'échappement, qui interdit tout retour à la ligne dans le texte, suffit à garantir
// que rien n'en sortira. Le bloc, lui, se termine par une ligne « end note » qu'un texte pourrait
// imiter — d'où PlantUmlEscaper.noteBloc, qui rend cette imitation impossible.
public final class NoteRenderer {

    private NoteRenderer() {}

    public static String note(UmlNote note, String alias) {
        if (!note.isOnMember()) {
            // Le texte occupe à lui seul la ligne dessinée : c'est donc debutDeLigne et non
            // label, sans quoi une note valant « * voir le contrat » perdrait son astérisque, lue
            // comme une puce de liste, et « # Attention » se dessinerait « 1. Attention ».
            return "note " + placement(note.direction()) + " of " + alias + " : "
                + PlantUmlEscaper.debutDeLigne(note.text()) + "\n";
        }

        // Le membre visé se désigne « Alias::membre ». Il est échappé comme le reste : c'est un
        // nom Java, donc inoffensif en pratique, mais le rendu ne fait pas de pari sur ce qui
        // lui arrive.
        return "note " + placementSurMembre(note.direction()) + " of " + alias + "::"
            + PlantUmlEscaper.label(note.anchor()) + "\n"
            + PlantUmlEscaper.noteBloc(note.text()) + "\n"
            + "end note\n";
    }

    // Une contrainte de classe, dessinée comme une bulle.
    //
    // PlantUML n'a pas de notation propre pour une contrainte d'élément ; UML, lui, la dessine
    // entre accolades à côté de la classe, ce qu'une note reproduit exactement. Les accolades
    // viennent de NotationContrainte, seul endroit où cette notation est écrite.
    //
    // La ligne dessinée s'ouvre alors sur l'accolade, que nous produisons et qui n'est pas un
    // marqueur : le texte de l'utilisateur ne commence jamais la ligne ici, et debutDeLigne
    // n'aurait rien à y neutraliser.
    //
    // La forme d'une seule ligne convient, une note de classe n'ayant pas de terminateur.
    public static String contrainte(String regle, String alias) {
        return "note " + placement(UMLDirection.NONE) + " of " + alias + " : "
            + NotationContrainte.une(regle) + "\n";
    }

    // PlantUML exige un côté : « note of X » n'existe pas. NONE doit donc se traduire par un
    // choix, et c'est la droite — le placement d'usage d'une note, et celui qui perturbe le
    // moins la lecture verticale de l'héritage, qui occupe le haut et le bas.
    //
    // Les mots sont écrits en toutes lettres plutôt que dérivés du nom de l'énuméré : UP et DOWN
    // se disent « top » et « bottom » dans le vocabulaire de PlantUML, et une mise en minuscules
    // ne le devinerait pas.
    private static String placement(UMLDirection direction) {
        return switch (direction) {
            case NONE, RIGHT -> "right";
            case LEFT -> "left";
            case UP -> "top";
            case DOWN -> "bottom";
        };
    }

    // Le même vocabulaire, amputé de ce qu'une note de membre ne sait pas faire.
    //
    // PlantUML ne place une note de membre qu'à gauche ou à droite : « note top of A::m » n'est
    // pas une syntaxe dégradée, c'est une syntaxe refusée, et son refus emporte le document
    // entier — l'écrire produirait une image d'erreur à la place du diagramme, sans que rien
    // n'ait échoué. UP et DOWN se rabattent donc sur la droite, comme NONE.
    //
    // Perdre l'orientation demandée est un moindre mal ; le taire n'en serait pas un. C'est le
    // processeur d'annotations qui prévient, à la compilation et à l'endroit où la direction est
    // écrite (voir VerificateurNote).
    private static String placementSurMembre(UMLDirection direction) {
        return direction == UMLDirection.LEFT ? "left" : "right";
    }
}
