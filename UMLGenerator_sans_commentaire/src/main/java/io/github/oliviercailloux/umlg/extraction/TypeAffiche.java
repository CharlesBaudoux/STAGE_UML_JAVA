package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

public final class TypeAffiche {

    private static final Class<?>[] CONTENEURS =
        {Iterable.class, Optional.class};

    private static final Map<Class<?>, String> PRIMITIFS = Map.of(
        Boolean.class, "boolean", Byte.class, "byte", Character.class, "char",
        Short.class, "short", Integer.class, "int", Long.class, "long",
        Float.class, "float", Double.class, "double");

    private TypeAffiche() {}

    public static String de(Type type, Regles regles) {
        checkNotNull(type);
        checkNotNull(regles);

        if (type instanceof Class<?> classe) {
            return regles.nomAffiche(classe);
        }
        if (type instanceof ParameterizedType parametre) {
            StringJoiner arguments = new StringJoiner(", ", "<", ">");
            for (Type argument : parametre.getActualTypeArguments()) {
                arguments.add(de(argument, regles));
            }
            return de(parametre.getRawType(), regles) + arguments;
        }
        if (type instanceof GenericArrayType tableau) {
            return de(tableau.getGenericComponentType(), regles) + "[]";
        }
        if (type instanceof WildcardType joker) {
            Type[] hautes = joker.getUpperBounds();
            if (hautes.length == 1 && hautes[0] != Object.class) {
                return "? extends " + de(hautes[0], regles);
            }
            return "?";
        }
        if (type instanceof TypeVariable<?> variable) {
            return variable.getName();
        }
        return type.getTypeName();
    }

    public static String deLElement(Type type, Regles regles) {
        checkNotNull(type);
        checkNotNull(regles);

        if (!(type instanceof ParameterizedType parametre)) {
            return de(type, regles);
        }
        Type[] arguments = parametre.getActualTypeArguments();
        if (arguments.length != 1 || !estUnConteneur(parametre.getRawType())) {
            return de(type, regles);
        }
        if (arguments[0] instanceof Class<?> classe) {
            String primitif = PRIMITIFS.get(classe);
            if (primitif != null && regles.nomAffiche(classe).equals(classe.getSimpleName())) {
                return primitif;
            }
        }
        return de(arguments[0], regles);
    }

    public static String multipliciteDeduite(Type type) {
        checkNotNull(type);

        if (!(type instanceof ParameterizedType parametre)) {
            return "";
        }
        if (parametre.getActualTypeArguments().length != 1
            || !estUnConteneur(parametre.getRawType())) {
            return "";
        }
        Class<?> brut = (Class<?>) parametre.getRawType();
        return Optional.class.isAssignableFrom(brut) ? "0..1" : "0..*";
    }

    public static Class<?> elementSeul(Type type) {
        checkNotNull(type);

        if (!(type instanceof ParameterizedType parametre)) {
            return null;
        }
        Type[] arguments = parametre.getActualTypeArguments();
        if (arguments.length != 1 || !estUnConteneur(parametre.getRawType())) {
            return null;
        }
        return arguments[0] instanceof Class<?> classe ? classe : null;
    }

    private static boolean estUnConteneur(Type brut) {
        if (!(brut instanceof Class<?> classe)) {
            return false;
        }
        for (Class<?> conteneur : CONTENEURS) {
            if (conteneur.isAssignableFrom(classe)) {
                return true;
            }
        }
        return false;
    }
}
