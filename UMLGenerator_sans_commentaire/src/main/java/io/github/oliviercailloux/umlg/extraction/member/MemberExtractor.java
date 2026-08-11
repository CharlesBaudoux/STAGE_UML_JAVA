package io.github.oliviercailloux.umlg.extraction.member;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.ConstraintExtractor;
import io.github.oliviercailloux.umlg.extraction.MembresOrdonnes;
import io.github.oliviercailloux.umlg.extraction.TypeAffiche;
import io.github.oliviercailloux.umlg.extraction.relation.CibleDeduite;
import io.github.oliviercailloux.umlg.extraction.relation.TargetResolver;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MemberExtractor {

    private MemberExtractor() {}

    public static ImmutableList<String> extractLiterals(Class<?> clazz, UMLVisibility strategy) {
        ImmutableList.Builder<String> result = ImmutableList.builder();

        Object[] constantes = clazz.getEnumConstants();
        if (constantes == null) {
            return result.build();
        }

        for (Object constante : constantes) {
            Field field;
            try {
                field = clazz.getDeclaredField(((Enum<?>) constante).name());
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e);
            }
            boolean visible = VisibilityPolicy.estVisible(field.getModifiers(), strategy,
                field.isAnnotationPresent(UMLInclude.class),
                field.isAnnotationPresent(UMLExclude.class));

            if (visible) {
                result.add(field.getName());
            }
        }
        return result.build();
    }

    public static ImmutableList<UmlField> extractFields(Class<?> clazz, UMLVisibility strategy) {
        return extractFields(clazz, strategy, Regles.aucune());
    }

    public static ImmutableList<UmlField> extractFields(Class<?> clazz, UMLVisibility strategy,
        Regles regles) {
        ImmutableList.Builder<UmlField> result = ImmutableList.builder();

        for (Field field : MembresOrdonnes.champs(clazz, regles)) {

            if (field.isSynthetic()) {
                continue;
            }

            if (field.isEnumConstant()) {
                continue;
            }

            UMLAssociation association = field.getAnnotation(UMLAssociation.class);

            checkArgument(association == null || !field.isAnnotationPresent(UMLMultiplicity.class),
                "%s carries both @UMLAssociation and @UMLMultiplicity: the multiplicity of this "
                    + "property is already given by targetCardinality. Keep only one of the two.",
                emplacement(field));

            if (association != null && association.notation() != UMLNotation.ATTRIBUTE) {
                continue;
            }

            if (regles.membreMasque(field.getName())
                && !field.isAnnotationPresent(UMLInclude.class)) {
                continue;
            }

            if (!estVisible(field, association, strategy)) {
                continue;
            }
            result.add(association == null
                ? champ(field, regles)
                : champAssocie(field, association, regles));
        }
        return result.build();
    }

    private static boolean estVisible(Field field, UMLAssociation association,
        UMLVisibility strategy) {
        if (association != null) {
            return !field.isAnnotationPresent(UMLExclude.class);
        }
        if (estComposantDeRecord(field)) {
            return !field.isAnnotationPresent(UMLExclude.class);
        }
        return VisibilityPolicy.estVisible(field.getModifiers(), strategy,
            field.isAnnotationPresent(UMLInclude.class),
            field.isAnnotationPresent(UMLExclude.class));
    }

    private static boolean estComposantDeRecord(Field field) {
        Class<?> classe = field.getDeclaringClass();
        if (!classe.isRecord()) {
            return false;
        }
        for (RecordComponent composant : classe.getRecordComponents()) {
            if (composant.getName().equals(field.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean estAccesseurPropage(Class<?> clazz, Method method) {
        if (!clazz.isRecord() || method.getParameterCount() != 0
            || !method.isAnnotationPresent(UMLInclude.class)) {
            return false;
        }
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getName().equals(method.getName()) && estComposantDeRecord(field)) {
                return field.isAnnotationPresent(UMLInclude.class);
            }
        }
        return false;
    }

    private static UmlField champ(Field field, Regles regles) {
        try {
            String multiplicite = multiplicite(field, field.getGenericType(), regles);
            return new UmlField(
                nomAffiche(field),
                multiplicite.isEmpty()
                    ? TypeAffiche.de(field.getGenericType(), regles)
                    : TypeAffiche.deLElement(field.getGenericType(), regles),
                multiplicite,
                symbole(field.getModifiers(), regles),
                Modifier.isStatic(field.getModifiers()),
                field.isAnnotationPresent(UMLDerived.class),
                ConstraintExtractor.de(field, emplacement(field)));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                "@UMLMultiplicity sur " + emplacement(field) + " : " + e.getMessage(), e);
        }
    }

    private static String multiplicite(AnnotatedElement membre) {
        UMLMultiplicity annotation = membre.getAnnotation(UMLMultiplicity.class);
        return annotation == null ? "" : annotation.value();
    }

    private static String multiplicite(AnnotatedElement membre, java.lang.reflect.Type type,
        Regles regles) {
        String declaree = multiplicite(membre);
        if (!declaree.isEmpty() || !regles.multiplicitesDeduites()) {
            return declaree;
        }
        return TypeAffiche.multipliciteDeduite(type);
    }

    public static String nomAffiche(Field field) {
        UMLAssociation association = field.getAnnotation(UMLAssociation.class);
        String renomme = renomme(field, emplacement(field));

        if (association != null && association.notation() == UMLNotation.ATTRIBUTE
            && !association.targetRole().isEmpty()) {
            checkArgument(renomme.isEmpty(),
                "%s carries both targetRole = \"%s\" and @UMLInclude(name = \"%s\"): two names "
                    + "for the same line. Keep only one of the two.",
                emplacement(field), association.targetRole(), renomme);
            return association.targetRole();
        }
        return renomme.isEmpty() ? field.getName() : renomme;
    }

    public static String nomAffiche(Method method) {
        String renomme = renomme(method,
            method.getDeclaringClass().getName() + "." + method.getName());
        return renomme.isEmpty() ? method.getName() : renomme;
    }

    private static String renomme(AnnotatedElement membre, String emplacement) {
        UMLInclude inclusion = membre.getAnnotation(UMLInclude.class);
        if (inclusion == null || inclusion.name().isEmpty()) {
            return "";
        }
        checkArgument(!inclusion.name().isBlank(),
            "%s: @UMLInclude(name = ...) cannot be made of whitespace only.", emplacement);
        return inclusion.name();
    }

    private static UmlField champAssocie(Field field, UMLAssociation association,
        Regles regles) {
        String contexte = "@UMLAssociation sur " + emplacement(field);
        Class<?> cible = CibleDeduite.de(field, association, contexte);
        TargetResolver.validerCible(cible, contexte, "target()");

        try {
            UmlAssociation lien =
                UmlAssociation.builder(field.getName(), cible.getName())
                    .targetRole(association.targetRole())
                    .targetCardinality(association.targetCardinality())
                    .constraints(ConstraintExtractor.de(field, emplacement(field)))
                    .build();

            ImmutableList<String> contraintes = lien.constraints();

            return new UmlField(
                nomAffiche(field),
                cible.getSimpleName(),
                lien.targetCardinality(),
                symbole(field.getModifiers(), regles),
                Modifier.isStatic(field.getModifiers()),
                field.isAnnotationPresent(UMLDerived.class),
                contraintes);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(contexte + " : " + e.getMessage(), e);
        }
    }

    private static String retour(Method method, String multiplicite, Regles regles) {
        if (regles.nomAfficheRetour(method.getReturnType()).isEmpty()) {
            return "";
        }
        return multiplicite.isEmpty()
            ? TypeAffiche.de(method.getGenericReturnType(), regles)
            : TypeAffiche.deLElement(method.getGenericReturnType(), regles);
    }

    private static String symbole(int modifiers, Regles regles) {
        return regles.omettreVisibilite() ? "" : VisibilityPolicy.symbole(modifiers);
    }

    private static String emplacement(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }

    private static ImmutableList<UmlParameter> parametres(Method method, Regles regles) {
        ImmutableList.Builder<UmlParameter> parametres = ImmutableList.builder();

        for (Parameter parametre : method.getParameters()) {
            String type = TypeAffiche.de(parametre.getParameterizedType(), regles);

            parametres.add(parametre.isNamePresent() && !regles.omettreNomsDeParametres()
                ? new UmlParameter(parametre.getName(), type)
                : UmlParameter.anonyme(type));
        }
        return parametres.build();
    }

    public static ImmutableList<UmlMethod> extractMethods(Class<?> clazz, UMLVisibility strategy) {
        return extractMethods(clazz, strategy, Regles.aucune());
    }

    public static ImmutableList<UmlMethod> extractMethods(Class<?> clazz, UMLVisibility strategy,
        Regles regles) {
        ImmutableList.Builder<UmlMethod> result = ImmutableList.builder();

        for (Method method : MembresOrdonnes.methodes(clazz, regles)) {

            if (method.isSynthetic()) {
                continue;
            }

            if (estAccesseurPropage(clazz, method)) {
                continue;
            }

            UMLInclude inclusion = method.getAnnotation(UMLInclude.class);

            if (inclusion != null && inclusion.asAttribute()) {
                continue;
            }

            boolean hasInclude = inclusion != null;
            boolean hasExclude = method.isAnnotationPresent(UMLExclude.class)
                || excluParHeritage(clazz, method);

            if (regles.membreMasque(method.getName()) && !hasInclude) {
                continue;
            }

            if (VisibilityPolicy.estVisible(method.getModifiers(), strategy, hasInclude,
                hasExclude)) {

                try {
                    String multiplicite =
                        multiplicite(method, method.getGenericReturnType(), regles);
                    result.add(new UmlMethod(
                        nomAffiche(method),
                        retour(method, multiplicite, regles),
                        multiplicite,
                        symbole(method.getModifiers(), regles),
                        Modifier.isStatic(method.getModifiers()),
                        parametres(method, regles),
                        method.isAnnotationPresent(UMLDerived.class),
                        Modifier.isAbstract(method.getModifiers()) && !regles.omettreAbstrait()
                    ));
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw new IllegalArgumentException("@UMLMultiplicity sur "
                        + method.getDeclaringClass().getName() + "." + method.getName() + " : "
                        + e.getMessage(), e);
                }
            }
        }
        return result.build();
    }

    public static ImmutableList<UmlField> methodesEnAttributs(Class<?> clazz, Regles regles) {
        ImmutableList.Builder<UmlField> result = ImmutableList.builder();

        for (Method method : MembresOrdonnes.methodes(clazz, regles)) {
            UMLInclude inclusion = method.getAnnotation(UMLInclude.class);
            if (method.isSynthetic() || inclusion == null || !inclusion.asAttribute()) {
                continue;
            }
            if (estAccesseurPropage(clazz, method)) {
                continue;
            }
            if (method.isAnnotationPresent(UMLExclude.class) || excluParHeritage(clazz, method)) {
                continue;
            }
            String emplacement = clazz.getName() + "." + method.getName();
            checkArgument(method.getParameterCount() == 0,
                "%s carries @UMLInclude(asAttribute = true) but takes parameters: an attribute "
                    + "has none. Remove the parameters, or the notation.", emplacement);
            checkArgument(method.getReturnType() != void.class,
                "%s carries @UMLInclude(asAttribute = true) but returns nothing: an attribute "
                    + "is its type. Return something, or remove the notation.", emplacement);

            try {
                String multiplicite =
                    multiplicite(method, method.getGenericReturnType(), regles);
                result.add(new UmlField(
                    nomAffiche(method),
                    multiplicite.isEmpty()
                        ? TypeAffiche.de(method.getGenericReturnType(), regles)
                        : TypeAffiche.deLElement(method.getGenericReturnType(), regles),
                    multiplicite,
                    symbole(method.getModifiers(), regles),
                    Modifier.isStatic(method.getModifiers()),
                    method.isAnnotationPresent(UMLDerived.class),
                    ConstraintExtractor.de(method, emplacement)));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException(
                    "@UMLMultiplicity sur " + emplacement + " : " + e.getMessage(), e);
            }
        }
        return result.build();
    }

    private static boolean excluParHeritage(Class<?> clazz, Method method) {
        if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers())) {
            return false;
        }
        for (Class<?> surtype : surtypes(clazz)) {
            try {
                if (surtype.getDeclaredMethod(method.getName(), method.getParameterTypes())
                    .isAnnotationPresent(UMLExclude.class)) {
                    return true;
                }
            } catch (NoSuchMethodException e) {
            }
        }
        return false;
    }

    private static Set<Class<?>> surtypes(Class<?> clazz) {
        Set<Class<?>> vus = new LinkedHashSet<>();
        List<Class<?>> aVisiter = new ArrayList<>();
        aVisiter.add(clazz);

        while (!aVisiter.isEmpty()) {
            Class<?> courant = aVisiter.remove(aVisiter.size() - 1);
            Class<?> parent = courant.getSuperclass();
            if (parent != null && vus.add(parent)) {
                aVisiter.add(parent);
            }
            for (Class<?> interface_ : courant.getInterfaces()) {
                if (vus.add(interface_)) {
                    aVisiter.add(interface_);
                }
            }
        }
        return vus;
    }
}
