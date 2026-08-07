package io.github.oliviercailloux.umlg.extraction.relation;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.extraction.TypeAffiche;
import java.lang.reflect.Field;

// La classe visée par une association : celle qu'on a écrite, ou celle que le type de l'attribut
// désigne.
//
// POURQUOI DÉDUIRE ICI EST LÉGITIME
//
// Le principe de la bibliothèque est que rien n'est deviné. Lire « List<Commande> » n'est pas
// deviner : c'est lire ce que l'auteur a écrit, à l'endroit où il l'a écrit, et le compilateur
// garantit que c'est exact. Ce qui est réellement indéductible — la multiplicité, qui relève du
// métier et non du code — reste déclaré.
//
// Le gain est double : une annotation de moins à écrire dans le cas courant, et surtout une
// source de vérité au lieu de deux. Une cible déclarée qui ne correspond pas au type de
// l'attribut est un écart que rien n'oblige à commettre si l'on ne la déclare plus.
public final class CibleDeduite {

    private CibleDeduite() {}

    // Vrai quand l'utilisateur n'a pas écrit de cible.
    public static boolean absente(UMLAssociation association) {
        return association.target() == void.class;
    }

    // La cible effective de cette association.
    //
    // Trois cas, dans cet ordre : elle est écrite, on la prend ; le type de l'attribut est une
    // collection paramétrée d'une seule classe, c'est elle ; le type de l'attribut est une classe
    // ordinaire, c'est lui.
    //
    // Reste ce qui n'a pas de réponse : un type brut, une Map dont rien ne dit laquelle des deux
    // moitiés est visée, un tableau de types variables. La cible doit alors être écrite, et le
    // message le demande en nommant l'attribut.
    public static Class<?> de(Field field, UMLAssociation association, String contexte) {
        if (!absente(association)) {
            return association.target();
        }
        Class<?> element = TypeAffiche.elementSeul(field.getGenericType());
        if (element != null) {
            return element;
        }
        // Un type qui n'est pas paramétré est lui-même la cible : « private Commande courante ».
        Class<?> declare = field.getType();
        checkArgument(!declare.isPrimitive() && !declare.isArray(),
            "%s: target() is not written, and the field's type (%s) does not name a class of the "
                + "diagram. Write target explicitly.",
            contexte, declare.getSimpleName());

        // Une collection sans paramètre de type ne dit rien de son contenu : c'est exactement le
        // manque que target vient combler, et le seul cas où l'annotation reste indispensable.
        checkArgument(!Iterable.class.isAssignableFrom(declare),
            "%s: target() is not written, and the field's type (%s) does not say what it "
                + "contains. Write target explicitly, or give the collection a type parameter.",
            contexte, declare.getSimpleName());

        return declare;
    }
}
