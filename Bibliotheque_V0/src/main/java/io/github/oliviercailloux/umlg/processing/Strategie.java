package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;

// Ce que le processeur sait de la stratégie d'une classe.
//
// LE PROBLÈME QU'IL RÉSOUT
//
// Le défaut de @UMLClass est PROJECT : « la stratégie que déclare le fichier umlg.rules ». Or ce
// fichier vit à la racine du répertoire des classes compilées, et javac ne sait pas où il se
// trouve — il compile des sources, et rien ne lui dit ce que la construction fera de leur
// résultat. Le processeur ne peut donc pas savoir, de lui-même, si un membre sera dessiné.
//
// Tous les avertissements qui disent « ce membre n'est pas affiché » ou « il l'est déjà »
// deviennent alors indécidables. Ils se taisent, faute de pouvoir conclure : un avertissement qui
// se trompe coûte plus cher que celui qui manque un cas, puisqu'il apprend à ne plus les lire.
//
// COMMENT ON LUI REND LA VUE
//
// L'utilisateur peut donner le chemin du fichier à javac, par l'option -Aumlg.rules. Le
// processeur lit alors les mêmes règles que la génération, et retrouve la parole : rien de ce
// qu'il aurait dit n'est perdu. C'est la seule chose que la bibliothèque taise, et c'est la
// seule ligne de configuration qui l'en empêche.
final class Strategie {

    // Les règles du projet, ou null quand l'option n'a pas été donnée. Null plutôt qu'un jeu vide
    // : « aucune règle » et « des règles qui ne disent rien » mènent au même diagramme, mais pas
    // au même savoir — le second permet de conclure, le premier non.
    private final Regles regles;

    Strategie(Regles regles) {
        this.regles = regles;
    }

    // La stratégie de cette classe est-elle connaissable ici ?
    //
    // Elle l'est dès que la classe la déclare — le fichier n'a alors rien à dire —, et sinon dès
    // que le processeur a reçu les règles du projet.
    boolean connue(UMLClass classe) {
        return classe.strategy() != UMLVisibility.PROJECT || regles != null;
    }

    // La stratégie qui s'appliquera réellement à cette classe.
    //
    // À n'appeler que lorsque connue() a répondu vrai : autrement, la réponse serait une
    // supposition. PROJECT sans règles rend NONE, le repli le plus sûr — une boîte vide se
    // remarque, un membre affiché par erreur non — mais aucun message ne devrait en dépendre.
    UMLVisibility effective(UMLClass classe) {
        if (classe.strategy() != UMLVisibility.PROJECT) {
            return classe.strategy();
        }
        return regles == null ? UMLVisibility.NONE : regles.strategie();
    }
}
