package io.github.oliviercailloux.umlg.processing;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

// Ce que plusieurs vérificateurs ont besoin de savoir d'un attribut ou d'une méthode.
//
// Mis en commun parce que deux formulations différentes de la même question finiraient par
// diverger : « com.banque.Client.solde » doit se dire pareil quel que soit le contrôle qui parle.
final class Membre {

    private Membre() {}

    static int bits(Element membre) {
        int bits = 0;
        for (Modifier modificateur : membre.getModifiers()) {
            switch (modificateur) {
                case PUBLIC -> bits |= java.lang.reflect.Modifier.PUBLIC;
                case PROTECTED -> bits |= java.lang.reflect.Modifier.PROTECTED;
                case PRIVATE -> bits |= java.lang.reflect.Modifier.PRIVATE;
                default -> { }
            }
        }
        return bits;
    }

    static boolean estAttribut(Element membre) {
        return membre.getKind() == ElementKind.FIELD
            || membre.getKind() == ElementKind.ENUM_CONSTANT;
    }

    static String emplacement(Element membre) {
        return ((TypeElement) membre.getEnclosingElement()).getQualifiedName() + "."
            + membre.getSimpleName();
    }
}
