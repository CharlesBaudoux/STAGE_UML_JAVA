package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import javax.lang.model.element.AnnotationMirror;

// Lit le paramètre notation() d'une @UMLAssociation, à la compilation.
//
// La question « ce lien sera-t-il un trait ? » se pose à quatre vérificateurs, qui n'ont rien
// d'autre en commun : celui des associations, celui des dérogations, celui de @UMLDerived et
// celui des dépendances. Quatre lectures d'un même paramètre, c'était quatre occasions de se
// tromper sur le défaut ; il n'y en a plus qu'une.
//
// Un membre d'annotation étant lu par miroir, la valeur d'une énumération arrive sous la forme
// d'un VariableElement dont le texte est le nom de la constante — « ATTRIBUTE ». La comparaison
// porte donc sur ce nom, et non sur la constante elle-même.
final class Notation {

    private Notation() {}

    // Vrai si l'association se dessine en trait entre deux boîtes, faux si elle s'écrit dans la
    // boîte de départ. Une notation illisible — ce que le compilateur refuserait de toute façon —
    // est traitée comme le défaut, ARROW.
    static boolean trait(Rapporteur rapporteur, AnnotationMirror association) {
        return !UMLNotation.ATTRIBUTE.name().equals(rapporteur.texte(association, "notation"));
    }
}
