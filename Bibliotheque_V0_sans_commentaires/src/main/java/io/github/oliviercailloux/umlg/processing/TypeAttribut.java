package io.github.oliviercailloux.umlg.processing;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

// Ce que le type d'un attribut laisse deviner, du point de vue du diagramme.
//
// Rien ici ne décide de ce qui sera dessiné : la bibliothèque ne déduit jamais une relation d'un
// type — c'est @UMLAssociation qui dit tout, target() comprise. Ces deux questions ne servent
// donc qu'aux avertissements : comparer ce que l'annotation déclare à ce que le type suggère,
// et parler quand les deux se contredisent.
//
// La comparaison est possible ici, et seulement ici. Le processeur voit le type source complet,
// paramètres génériques inclus ; l'extraction, elle, travaille sur des classes compilées où
// « List<Commande> » n'est plus que « List ».
final class TypeAttribut {

    private final Types types;
    private final TypeMirror collection;
    private final TypeMirror map;

    TypeAttribut(Types types, Elements elements) {
        this.types = types;
        this.collection = erasureDe(types, elements, "java.util.Collection");
        this.map = erasureDe(types, elements, "java.util.Map");
    }

    boolean multivalue(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return true;
        }
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        TypeMirror brut = types.erasure(type);
        return (collection != null && types.isAssignable(brut, collection))
            || (map != null && types.isAssignable(brut, map));
    }

    boolean contient(TypeMirror type, TypeMirror cible) {
        return switch (type.getKind()) {
            case ARRAY -> contient(((ArrayType) type).getComponentType(), cible);
            case WILDCARD -> {
                WildcardType joker = (WildcardType) type;
                TypeMirror borne = joker.getExtendsBound() != null
                    ? joker.getExtendsBound()
                    : joker.getSuperBound();
                yield borne != null && contient(borne, cible);
            }
            case DECLARED -> {
                if (types.isSameType(types.erasure(type), types.erasure(cible))) {
                    yield true;
                }
                for (TypeMirror argument : ((DeclaredType) type).getTypeArguments()) {
                    if (contient(argument, cible)) {
                        yield true;
                    }
                }
                yield false;
            }
            case TYPEVAR -> true;
            default -> false;
        };
    }

    boolean comparableALaCible(TypeMirror type) {
        if (indetermine(type)) {
            return false;
        }
        return !(type.getKind() == TypeKind.DECLARED && multivalue(type)
            && ((DeclaredType) type).getTypeArguments().isEmpty());
    }

    boolean indetermine(TypeMirror type) {
        return type.getKind() == TypeKind.TYPEVAR
            || type.getKind() == TypeKind.ERROR
            || (type.getKind() == TypeKind.DECLARED
                && types.erasure(type).toString().equals("java.lang.Object"));
    }

    private static TypeMirror erasureDe(Types types, Elements elements, String nom) {
        TypeElement element = elements.getTypeElement(nom);
        return element == null ? null : types.erasure(element.asType());
    }
}
