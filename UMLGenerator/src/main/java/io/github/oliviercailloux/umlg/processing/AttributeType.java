package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.extraction.relation.InferredTarget;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

final class AttributeType {
    private final Types types;

    private final TypeMirror collection;
    private final TypeMirror map;

    private final TypeMirror iterable;
    private final TypeMirror optional;

    AttributeType(Types types, Elements elements) {
        this.types = types;
        this.collection = erasure(types, elements, "java.util.Collection");
        this.map = erasure(types, elements, "java.util.Map");
        this.iterable = erasure(types, elements, "java.lang.Iterable");
        this.optional = erasure(types, elements, "java.util.Optional");
    }

    boolean multivalue(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return true;
        }
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        TypeMirror raw = types.erasure(type);
        return (collection != null && types.isAssignable(raw, collection))
            || (map != null && types.isAssignable(raw, map));
    }

    boolean contains(TypeMirror type, TypeMirror target) {
        return switch (type.getKind()) {
            case ARRAY -> contains(((ArrayType) type).getComponentType(), target);
            case WILDCARD -> {
                WildcardType wildcard = (WildcardType) type;
                TypeMirror bound = wildcard.getExtendsBound() != null
                    ? wildcard.getExtendsBound()
                    : wildcard.getSuperBound();
                yield bound != null && contains(bound, target);
            }
            case DECLARED -> {
                if (types.isSameType(types.erasure(type), types.erasure(target))) {
                    yield true;
                }
                for (TypeMirror argument : ((DeclaredType) type).getTypeArguments()) {
                    if (contains(argument, target)) {
                        yield true;
                    }
                }
                yield false;
            }

            case TYPEVAR -> true;
            default -> false;
        };
    }

    TypeMirror loneElement(TypeMirror type) {
        TypeMirror examines = type.getKind() == TypeKind.TYPEVAR ? types.erasure(type) : type;
        if (examines.getKind() != TypeKind.DECLARED || undetermined(examines)) {
            return null;
        }
        DeclaredType declare = (DeclaredType) examines;
        if (!isAContainer(examines)) {
            return types.erasure(examines);
        }

        if (declare.getTypeArguments().size() != 1) {
            return null;
        }
        TypeMirror argument = declare.getTypeArguments().get(0);
        return argument.getKind() == TypeKind.DECLARED ? types.erasure(argument) : null;
    }

    String complaintTargetUninferable(String context, TypeMirror type) {
        String name = nameSimple(type);

        return isIterable(type)
            ? InferredTarget.messageContentUninferable(context, name)
            : InferredTarget.messageTargetUninferable(context, name);
    }

    private String nameSimple(TypeMirror type) {
        TypeMirror raw = types.erasure(type);
        Element element = types.asElement(raw);
        return element == null ? raw.toString() : element.getSimpleName().toString();
    }

    private boolean isIterable(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED && iterable != null
            && types.isAssignable(types.erasure(type), iterable);
    }

    private boolean isAContainer(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        TypeMirror raw = types.erasure(type);
        return (iterable != null && types.isAssignable(raw, iterable))
            || (optional != null && types.isAssignable(raw, optional));
    }

    boolean comparableToTarget(TypeMirror type) {
        if (undetermined(type)) {
            return false;
        }
        return !(type.getKind() == TypeKind.DECLARED && (multivalue(type) || isAContainer(type))
            && ((DeclaredType) type).getTypeArguments().isEmpty());
    }

    boolean undetermined(TypeMirror type) {
        return type.getKind() == TypeKind.TYPEVAR
            || type.getKind() == TypeKind.ERROR
            || (type.getKind() == TypeKind.DECLARED
                && types.erasure(type).toString().equals("java.lang.Object"));
    }

    private static TypeMirror erasure(Types types, Elements elements, String name) {
        TypeElement element = elements.getTypeElement(name);

        return element == null ? null : types.erasure(element.asType());
    }
}
