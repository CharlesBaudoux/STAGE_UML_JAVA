package io.github.oliviercailloux.umlg.rendering.element;

import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;

import java.util.List;

// La notation UML d'une contrainte : la règle de l'utilisateur, entre accolades.
//
// POURQUOI CETTE CLASSE EXISTE
//
// La même notation s'écrivait à trois endroits — à la suite d'un membre dans une boîte, au bout
// d'un trait, dans la bulle d'une contrainte de classe — et trois fois de la même façon, avec le
// même commentaire recopié. Or le projet a déjà constaté deux fois que la règle écrite deux fois
// finit par diverger ; c'est pour cela que Multiplicites et ColorCodec existent. Les accolades et
// l'échappement de ce qu'elles entourent ne vivent donc plus qu'ici.
//
// Les accolades sont une notation que nous produisons, et ne passent pas par l'échappement ; la
// règle, elle, vient de l'utilisateur, qui l'écrit nue.
final class NotationContrainte {

    private NotationContrainte() {}

    // Une contrainte, accolades comprises : « {subset} ».
    static String une(String regle) {
        return "{" + PlantUmlEscaper.label(regle) + "}";
    }

    // Les contraintes d'un élément, chacune précédée d'une espace, à la suite de ce qu'elles
    // contraignent : « solde : double {positif} {audite} ». Rien du tout s'il n'y en a aucune,
    // l'espace disparaissant avec elles.
    static String aLaSuite(List<String> regles) {
        StringBuilder sortie = new StringBuilder();
        for (String regle : regles) {
            sortie.append(' ').append(une(regle));
        }
        return sortie.toString();
    }
}
