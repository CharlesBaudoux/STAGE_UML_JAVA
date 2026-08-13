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
import io.github.oliviercailloux.umlg.warning.Avertisseur;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MemberExtractor {

    private MemberExtractor() {}

    public static ImmutableList<String> extractLiterals(Class<?> clazz, UMLVisibility strategy) {
        return extractLiterals(clazz, strategy, Regles.aucune());
    }

    public static ImmutableList<String> extractLiterals(Class<?> clazz, UMLVisibility strategy,
        Regles regles) {
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
            retenu(field, strategy, regles).ifPresent(result::add);
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
                messageDeuxMultiplicites(emplacement(field)));

            signalerNotationAttributSurUnChamp(field);

            if (association != null && association.notation() != UMLNotation.ATTRIBUTE) {
                continue;
            }

            Optional<String> nom = retenu(field, strategy, regles);
            if (nom.isEmpty()) {
                continue;
            }
            result.add(association == null
                ? champ(field, nom.get(), regles)
                : champAssocie(field, nom.get(), association, regles));
        }
        return result.build();
    }

    private static Optional<String> retenu(Field field, UMLVisibility strategy, Regles regles) {
        boolean privilegie = field.isAnnotationPresent(UMLAssociation.class)
            || estComposantDeRecord(field);

        if (!entre(field.getName(), field.getModifiers(),
            field.isAnnotationPresent(UMLInclude.class),
            field.isAnnotationPresent(UMLExclude.class), privilegie, strategy, regles)) {
            return Optional.empty();
        }
        return Optional.of(nomAffiche(field));
    }

    private static Optional<String> retenu(Class<?> clazz, Method method, UMLVisibility strategy,
        Regles regles) {
        boolean exclu = method.isAnnotationPresent(UMLExclude.class)
            || excluParHeritage(clazz, method);

        if (!entre(method.getName(), method.getModifiers(),
            method.isAnnotationPresent(UMLInclude.class), exclu, false, strategy, regles)) {
            return Optional.empty();
        }
        return Optional.of(nomAffiche(method));
    }

    private static boolean entre(String nom, int modifiers, boolean inclus, boolean exclu,
        boolean privilegie, UMLVisibility strategy, Regles regles) {
        if (exclu) {
            return false;
        }
        if (inclus || privilegie) {
            return true;
        }
        return !regles.membreMasque(nom) && VisibilityPolicy.parLaStrategie(modifiers, strategy);
    }

    private static void signalerNotationAttributSurUnChamp(Field field) {
        UMLInclude inclusion = field.getAnnotation(UMLInclude.class);
        if (inclusion != null && inclusion.asAttribute()) {
            Avertisseur.dire(messageNotationAttributSurUnChamp(emplacement(field)));
        }
    }


    public static String messageDeuxMultiplicites(String emplacement) {
        return emplacement + " carries both @UMLAssociation and @UMLMultiplicity: the "
            + "multiplicity of this property is already given by targetMultiplicity. Keep only one "
            + "of the two.";
    }

    public static String messageNotationAttributSurUnChamp(String emplacement) {
        return emplacement + ": @UMLInclude(asAttribute = true) is for methods; a field is "
            + "already an attribute. Remove the attribute.";
    }

    public static String messageNotationAttributAvecParametres(String emplacement) {
        return emplacement + " carries @UMLInclude(asAttribute = true) but takes parameters: an "
            + "attribute has none. Remove the parameters, or the notation.";
    }

    public static String messageNotationAttributSansRetour(String emplacement) {
        return emplacement + " carries @UMLInclude(asAttribute = true) but returns nothing: an "
            + "attribute is its type. Return something, or remove the notation.";
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

    private static <M extends AccessibleObject & Member> UmlField attribut(M membre, String nom,
        String type, String multiplicite, ImmutableList<String> contraintes, Regles regles) {
        return new UmlField(
            nom,
            type,
            multiplicite,
            symbole(membre.getModifiers(), regles),
            Modifier.isStatic(membre.getModifiers()),
            membre.isAnnotationPresent(UMLDerived.class),
            contraintes);
    }

    private static String typeDeLaPropriete(Type type, String multiplicite, Regles regles) {
        return multiplicite.isEmpty()
            ? TypeAffiche.de(type, regles)
            : TypeAffiche.deLElement(type, regles);
    }

    private static UmlField champ(Field field, String nom, Regles regles) {
        try {
            String multiplicite = multiplicite(field, field.getGenericType(), regles);
            return attribut(field, nom,
                typeDeLaPropriete(field.getGenericType(), multiplicite, regles),
                multiplicite, ConstraintExtractor.de(field, emplacement(field)), regles);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                "@UMLMultiplicity sur " + emplacement(field) + " : " + e.getMessage(), e);
        }
    }

    private static String multiplicite(AnnotatedElement membre) {
        UMLMultiplicity annotation = membre.getAnnotation(UMLMultiplicity.class);
        return annotation == null ? "" : annotation.value();
    }

    private static String multiplicite(AnnotatedElement membre, Type type,
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

    private static UmlField champAssocie(Field field, String nom, UMLAssociation association,
        Regles regles) {
        String contexte = "@UMLAssociation sur " + emplacement(field);
        Class<?> cible = CibleDeduite.de(field, association, contexte);
        TargetResolver.validerCible(cible, contexte, "target()");

        try {
            UmlAssociation lien =
                UmlAssociation.builder(field.getName(), cible.getName())
                    .sourceMultiplicity(association.sourceMultiplicity())
                    .sourceRole(association.sourceRole())
                    .targetRole(association.targetRole())
                    .targetMultiplicity(association.targetMultiplicity())
                    .label(association.label())
                    .constraints(ConstraintExtractor.de(field, emplacement(field)))
                    .build();

            ImmutableList<String> contraintes = lien.constraints();

            return attribut(field, nom,
                cible.getSimpleName(),
                lien.targetMultiplicity(), contraintes, regles);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(contexte + " : " + e.getMessage(), e);
        }
    }

    private static String retour(Method method, String multiplicite, Regles regles) {
        if (regles.nomAfficheRetour(method.getReturnType()).isEmpty()) {
            return "";
        }
        return typeDeLaPropriete(method.getGenericReturnType(), multiplicite, regles);
    }

    private static String symbole(int modifiers, Regles regles) {
        return regles.omettreVisibilite() ? "" : VisibilityPolicy.symbole(modifiers);
    }

    private static String emplacement(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }

    private static String emplacement(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    private static ImmutableList<UmlParameter> parametres(Method method, Regles regles) {
        ImmutableList.Builder<UmlParameter> parametres = ImmutableList.builder();

        for (Parameter parametre : method.getParameters()) {
            String type = typeDuParametre(parametre, regles);

            parametres.add(parametre.isNamePresent() && !regles.omettreNomsDeParametres()
                ? new UmlParameter(parametre.getName(), type)
                : UmlParameter.unnamed(type));
        }
        return parametres.build();
    }

    private static String typeDuParametre(Parameter parametre, Regles regles) {
        String type = TypeAffiche.de(parametre.getParameterizedType(), regles);
        return parametre.isVarArgs() && type.endsWith("[]")
            ? type.substring(0, type.length() - "[]".length()) + "..."
            : type;
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

            Optional<String> nom = retenu(clazz, method, strategy, regles);
            if (nom.isEmpty()) {
                continue;
            }

            try {
                String multiplicite = multiplicite(method, method.getGenericReturnType(), regles);
                result.add(new UmlMethod(
                    nom.get(),
                    retour(method, multiplicite, regles),
                    multiplicite,
                    symbole(method.getModifiers(), regles),
                    Modifier.isStatic(method.getModifiers()),
                    parametres(method, regles),
                    method.isAnnotationPresent(UMLDerived.class),
                    Modifier.isAbstract(method.getModifiers()) && !regles.omettreAbstrait()
                ));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException(
                    "@UMLMultiplicity sur " + emplacement(method) + " : " + e.getMessage(), e);
            }
        }
        return result.build();
    }

    public static ImmutableList<UmlField> methodesEnAttributs(Class<?> clazz,
        UMLVisibility strategy, Regles regles) {
        ImmutableList.Builder<UmlField> result = ImmutableList.builder();

        for (Method method : MembresOrdonnes.methodes(clazz, regles)) {
            UMLInclude inclusion = method.getAnnotation(UMLInclude.class);
            if (method.isSynthetic() || inclusion == null || !inclusion.asAttribute()) {
                continue;
            }
            if (estAccesseurPropage(clazz, method)) {
                continue;
            }
            Optional<String> nom = retenu(clazz, method, strategy, regles);
            if (nom.isEmpty()) {
                continue;
            }
            String emplacement = emplacement(method);
            checkArgument(method.getParameterCount() == 0,
                messageNotationAttributAvecParametres(emplacement));
            checkArgument(method.getReturnType() != void.class,
                messageNotationAttributSansRetour(emplacement));

            try {
                String multiplicite =
                    multiplicite(method, method.getGenericReturnType(), regles);
                result.add(attribut(method, nom.get(),
                    typeDeLaPropriete(method.getGenericReturnType(), multiplicite, regles),
                    multiplicite, ConstraintExtractor.de(method, emplacement), regles));
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
            for (Method declaree : surtype.getDeclaredMethods()) {
                if (redefinit(method, declaree)
                    && declaree.isAnnotationPresent(UMLExclude.class)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean redefinit(Method candidate, Method declaree) {
        if (!declaree.getName().equals(candidate.getName())
            || declaree.getParameterCount() != candidate.getParameterCount()) {
            return false;
        }
        Class<?>[] attendus = declaree.getParameterTypes();
        Class<?>[] ecrits = candidate.getParameterTypes();
        for (int i = 0; i < attendus.length; i++) {
            if (!attendus[i].isAssignableFrom(ecrits[i])) {
                return false;
            }
        }
        return true;
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
