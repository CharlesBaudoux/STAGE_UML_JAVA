package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

public final class DisplayedType {
    private static final Class<?>[] Containers =
        {Iterable.class, Optional.class};

    private static final Map<Class<?>, String> Primitive = Map.of(
        Boolean.class, "boolean", Byte.class, "byte", Character.class, "char",
        Short.class, "short", Integer.class, "int", Long.class, "long",
        Float.class, "float", Double.class, "double");

    private DisplayedType() {}

    public static String of(Type type, Rules rules) {
        checkNotNull(type);
        checkNotNull(rules);

        if (type instanceof Class<?> clazz) {
            return rules.nameDisplayed(clazz);
        }
        if (type instanceof ParameterizedType parameter) {
            StringJoiner arguments = new StringJoiner(", ", "<", ">");
            for (Type argument : parameter.getActualTypeArguments()) {
                arguments.add(of(argument, rules));
            }
            return of(parameter.getRawType(), rules) + arguments;
        }
        if (type instanceof GenericArrayType array) {
            return of(array.getGenericComponentType(), rules) + "[]";
        }
        if (type instanceof WildcardType wildcard) {
            Type[] high = wildcard.getUpperBounds();
            if (high.length == 1 && high[0] != Object.class) {
                return "? extends " + of(high[0], rules);
            }
            return "?";
        }
        if (type instanceof TypeVariable<?> variable) {
            return variable.getName();
        }

        return type.getTypeName();
    }

    public static String element(Type type, Rules rules) {
        checkNotNull(type);
        checkNotNull(rules);

        ParameterizedType container = containerAAElement(type);
        if (container != null) {
            Type element = container.getActualTypeArguments()[0];

            if (element instanceof Class<?> clazz) {
                String primitive = Primitive.get(clazz);
                if (primitive != null && rules.nameDisplayed(clazz).equals(clazz.getSimpleName())) {
                    return primitive;
                }
            }
            return of(withoutWildcard(element), rules);
        }

        Type component = arrayComponent(type);
        if (component != null) {
            return of(component, rules);
        }
        return of(type, rules);
    }

    private static ParameterizedType containerAAElement(Type type) {
        if (!(type instanceof ParameterizedType parameter)) {
            return null;
        }
        if (parameter.getActualTypeArguments().length != 1
            || !isAContainer(parameter.getRawType())) {
            return null;
        }
        return parameter;
    }

    private static Type arrayComponent(Type type) {
        if (type instanceof GenericArrayType array) {
            return array.getGenericComponentType();
        }
        if (type instanceof Class<?> clazz && clazz.isArray()) {
            return clazz.getComponentType();
        }
        return null;
    }

    private static Type withoutWildcard(Type type) {
        if (type instanceof WildcardType wildcard) {
            Type[] high = wildcard.getUpperBounds();
            if (high.length == 1 && high[0] != Object.class) {
                return high[0];
            }
        }
        return type;
    }

    public static String inferredMultiplicity(Type type) {
        checkNotNull(type);

        ParameterizedType container = containerAAElement(type);
        if (container == null) {
            return "";
        }
        Class<?> raw = (Class<?>) container.getRawType();
        return Optional.class.isAssignableFrom(raw) ? "0..1" : "0..*";
    }

    public static Class<?> loneElement(Type type) {
        checkNotNull(type);

        ParameterizedType container = containerAAElement(type);
        if (container == null) {
            return null;
        }
        return container.getActualTypeArguments()[0] instanceof Class<?> clazz ? clazz : null;
    }

    private static boolean isAContainer(Type raw) {
        if (!(raw instanceof Class<?> clazz)) {
            return false;
        }
        for (Class<?> container : Containers) {
            if (container.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }
}
