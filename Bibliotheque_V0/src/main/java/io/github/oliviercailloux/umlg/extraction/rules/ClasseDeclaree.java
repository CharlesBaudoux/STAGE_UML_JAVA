package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;

// Une boîte que le fichier de règles déclare, et qu'aucune classe Java ne porte.
//
// POURQUOI CELA EXISTE
//
// Un diagramme n'est pas toujours le reflet exact du code. On veut y montrer une classe encore à
// écrire, ou une abstraction que le code réalise autrement : une hiérarchie de deux sous-classes
// explique parfois mieux qu'une classe unique à deux champs dont un seul est renseigné. Les
// diagrammes tenus à la main le font couramment, et s'en priver reviendrait à interdire de
// dessiner ce qu'on a en tête.
//
// CE QUE CELA COÛTE, ET COMMENT ON LE PAIE
//
// Une boîte déclarée ici échappe à tout ce qui fait la valeur de la bibliothèque : aucun
// compilateur ne la vérifie, aucun renommage ne la suit, et rien ne signalera le jour où le code
// aura pris un autre chemin. C'est exactement le défaut des diagrammes tenus à la main, et il
// serait malhonnête de le réintroduire en silence.
//
// D'où la contrepartie, non négociable : une boîte déclarée se voit. Elle porte le stéréotype
// « declared » et un contour pointillé, si bien qu'un lecteur distingue au premier coup d'œil ce
// qui a du code derrière de ce qui n'en a pas. Un diagramme mixte qui ne marquerait pas la
// différence serait pire que les deux extrêmes.
public record ClasseDeclaree(String nom, String parent, ImmutableList<ChampDeclare> champs) {

    // Un attribut d'une boîte déclarée : « nom : Type ». Rien de plus — ni visibilité, ni
    // multiplicité : ce qui n'existe pas dans le code n'a pas d'encapsulation à montrer.
    public record ChampDeclare(String nom, String type) {

        public ChampDeclare {
            checkNotNull(nom);
            checkNotNull(type);
            checkArgument(!nom.isBlank(), "The field name cannot be empty.");
            checkArgument(!type.isBlank(), "The field type cannot be empty.");
        }
    }

    public ClasseDeclaree {
        checkNotNull(nom);
        checkNotNull(parent, "The parent cannot be null; use an empty string for none.");
        checkNotNull(champs);
        checkArgument(!nom.isBlank(), "The declared class name cannot be empty.");
    }

    public static ClasseDeclaree de(String nom, String parent) {
        return new ClasseDeclaree(nom, parent, ImmutableList.of());
    }

    // La même, augmentée d'un attribut : le fichier les déclare une ligne à la fois.
    public ClasseDeclaree avec(ChampDeclare champ) {
        return new ClasseDeclaree(nom, parent,
            ImmutableList.<ChampDeclare>builder().addAll(champs).add(champ).build());
    }

    public boolean aUnParent() {
        return !parent.isEmpty();
    }
}
