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

    // Traduit les modificateurs de javac en l'entier de bits qu'attend VisibilityPolicy, celui
    // que la réflexion produirait sur le même membre.
    //
    // Seules les trois visibilités sont traduites : ce sont les seules que la règle consulte, et
    // l'absence des trois est justement ce qui dit « package-private ».
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

    // Les littéraux d'une énumération suivent le même chemin que les attributs.
    static boolean estAttribut(Element membre) {
        return membre.getKind() == ElementKind.FIELD
            || membre.getKind() == ElementKind.ENUM_CONSTANT;
    }

    static String emplacement(Element membre) {
        return ((TypeElement) membre.getEnclosingElement()).getQualifiedName() + "."
            + membre.getSimpleName();
    }
}
