package io.github.oliviercailloux.umlg.processing;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

final class TypeAttribut {

    private final Types types;
    private final TypeMirror collection;
    private final TypeMirror map;
    private final TypeMirror iterable;
    private final TypeMirror optional;

    TypeAttribut(Types types, Elements elements) {
        this.types = types;
        this.collection = erasureDe(types, elements, "java.util.Collection");
        this.map = erasureDe(types, elements, "java.util.Map");
        this.iterable = erasureDe(types, elements, "java.lang.Iterable");
        this.optional = erasureDe(types, elements, "java.util.Optional");
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

    TypeMirror elementSeul(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED || indetermine(type)) {
            return null;
        }
        DeclaredType declare = (DeclaredType) type;
        if (!estUnConteneur(type)) {
            return types.erasure(type);
        }
        if (declare.getTypeArguments().size() != 1) {
            return null;
        }
        TypeMirror argument = declare.getTypeArguments().get(0);
        return argument.getKind() == TypeKind.DECLARED ? types.erasure(argument) : null;
    }

    private boolean estUnConteneur(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        TypeMirror brut = types.erasure(type);
        return (iterable != null && types.isAssignable(brut, iterable))
            || (optional != null && types.isAssignable(brut, optional));
    }

    boolean comparableALaCible(TypeMirror type) {
        if (indetermine(type)) {
            return false;
        }
        return !(type.getKind() == TypeKind.DECLARED && (multivalue(type) || estUnConteneur(type))
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
