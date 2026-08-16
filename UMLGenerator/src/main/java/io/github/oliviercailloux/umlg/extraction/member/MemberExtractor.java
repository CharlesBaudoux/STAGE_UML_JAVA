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
import io.github.oliviercailloux.umlg.extraction.OrderedMembers;
import io.github.oliviercailloux.umlg.extraction.DisplayedType;
import io.github.oliviercailloux.umlg.extraction.relation.InferredTarget;
import io.github.oliviercailloux.umlg.extraction.relation.TargetResolver;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import io.github.oliviercailloux.umlg.warning.Warner;
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
        return extractLiterals(clazz, strategy, Rules.no());
    }

    public static ImmutableList<String> extractLiterals(Class<?> clazz, UMLVisibility strategy,
        Rules rules) {
        ImmutableList.Builder<String> result = ImmutableList.builder();

        Object[] constants = clazz.getEnumConstants();
        if (constants == null) {
            return result.build();
        }

        for (Object constant : constants) {
            Field field;
            try {
                field = clazz.getDeclaredField(((Enum<?>) constant).name());
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e);
            }
            kept(field, strategy, rules).ifPresent(result::add);
        }
        return result.build();
    }

    public static ImmutableList<UmlField> extractFields(Class<?> clazz, UMLVisibility strategy) {
        return extractFields(clazz, strategy, Rules.no());
    }

    public static ImmutableList<UmlField> extractFields(Class<?> clazz, UMLVisibility strategy,
        Rules rules) {
        ImmutableList.Builder<UmlField> result = ImmutableList.builder();

        for (Field field : OrderedMembers.fields(clazz, rules)) {
            if (field.isSynthetic()) {
                continue;
            }

            if (field.isEnumConstant()) {
                continue;
            }

            UMLAssociation association = field.getAnnotation(UMLAssociation.class);

            checkArgument(association == null || !field.isAnnotationPresent(UMLMultiplicity.class),
                messageTwoMultiplicities(location(field)));

            signalerNotationAttributSurUnChamp(field);

            if (association != null && association.notation() != UMLNotation.ATTRIBUTE) {
                continue;
            }

            Optional<String> name = kept(field, strategy, rules);
            if (name.isEmpty()) {
                continue;
            }
            result.add(association == null
                ? field(field, name.get(), rules)
                : fieldAssociated(field, name.get(), association, rules));
        }
        return result.build();
    }

    private static Optional<String> kept(Field field, UMLVisibility strategy, Rules rules) {
        boolean preferred = field.isAnnotationPresent(UMLAssociation.class)
            || recordIsComponent(field);

        if (!between(field.getName(), field.getModifiers(),
            field.isAnnotationPresent(UMLInclude.class),
            field.isAnnotationPresent(UMLExclude.class), preferred, strategy, rules)) {
            return Optional.empty();
        }
        return Optional.of(nameDisplayed(field));
    }

    private static Optional<String> kept(Class<?> clazz, Method method, UMLVisibility strategy,
        Rules rules) {
        boolean excluded = method.isAnnotationPresent(UMLExclude.class)
            || excludedByInheritance(clazz, method);

        if (!between(method.getName(), method.getModifiers(),
            method.isAnnotationPresent(UMLInclude.class), excluded, false, strategy, rules)) {
            return Optional.empty();
        }
        return Optional.of(nameDisplayed(method));
    }

    private static boolean between(String name, int modifiers, boolean included, boolean excluded,
        boolean preferred, UMLVisibility strategy, Rules rules) {
        if (excluded) {
            return false;
        }
        if (included || preferred) {
            return true;
        }

        return !rules.hiddenMember(name) && VisibilityPolicy.byStrategy(modifiers, strategy);
    }

    private static void signalerNotationAttributSurUnChamp(Field field) {
        UMLInclude inclusion = field.getAnnotation(UMLInclude.class);
        if (inclusion != null && inclusion.asAttribute()) {
            Warner.say(messageNotationAttributSurUnChamp(location(field)));
        }
    }

    public static String messageTwoMultiplicities(String location) {
        return location + " carries both @UMLAssociation and @UMLMultiplicity: the "
            + "multiplicity of this property is already given by targetMultiplicity. Keep only one "
            + "of the two.";
    }

    public static String messageNotationAttributSurUnChamp(String location) {
        return location + ": @UMLInclude(asAttribute = true) is for methods; a field is "
            + "already an attribute. Remove the attribute.";
    }

    public static String messageNotationAttributAvecParametres(String location) {
        return location + " carries @UMLInclude(asAttribute = true) but takes parameters: an "
            + "attribute has none. Remove the parameters, or the notation.";
    }

    public static String messageNotationAttributSansRetour(String location) {
        return location + " carries @UMLInclude(asAttribute = true) but returns nothing: an "
            + "attribute is its type. Return something, or remove the notation.";
    }

    private static boolean recordIsComponent(Field field) {
        Class<?> clazz = field.getDeclaringClass();
        if (!clazz.isRecord()) {
            return false;
        }
        for (RecordComponent component : clazz.getRecordComponents()) {
            if (component.getName().equals(field.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAccessorPropagated(Class<?> clazz, Method method) {
        if (!clazz.isRecord() || method.getParameterCount() != 0
            || !method.isAnnotationPresent(UMLInclude.class)) {
            return false;
        }
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getName().equals(method.getName()) && recordIsComponent(field)) {
                return field.isAnnotationPresent(UMLInclude.class);
            }
        }
        return false;
    }

    private static <M extends AccessibleObject & Member> UmlField attribute(M member, String name,
        String type, String multiplicity, ImmutableList<String> constraints, Rules rules) {
        return new UmlField(
            name,
            type,
            multiplicity,
            symbol(member.getModifiers(), rules),
            Modifier.isStatic(member.getModifiers()),
            member.isAnnotationPresent(UMLDerived.class),
            constraints);
    }

    private static String propertyType(Type type, String multiplicity, Rules rules) {
        return multiplicity.isEmpty()
            ? DisplayedType.of(type, rules)
            : DisplayedType.element(type, rules);
    }

    private static UmlField field(Field field, String name, Rules rules) {
        try {
            String multiplicity = multiplicity(field, field.getGenericType(), rules);
            return attribute(field, name,
                propertyType(field.getGenericType(), multiplicity, rules),
                multiplicity, ConstraintExtractor.of(field, location(field)), rules);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                "@UMLMultiplicity on " + location(field) + " : " + e.getMessage(), e);
        }
    }

    private static String multiplicity(AnnotatedElement member) {
        UMLMultiplicity annotation = member.getAnnotation(UMLMultiplicity.class);
        return annotation == null ? "" : annotation.value();
    }

    private static String multiplicity(AnnotatedElement member, Type type,
        Rules rules) {
        String declared = multiplicity(member);
        if (!declared.isEmpty() || !rules.inferredMultiplicities()) {
            return declared;
        }
        return DisplayedType.inferredMultiplicity(type);
    }

    public static String nameDisplayed(Field field) {
        UMLAssociation association = field.getAnnotation(UMLAssociation.class);
        String renamed = renamed(field, location(field));

        if (association != null && association.notation() == UMLNotation.ATTRIBUTE
            && !association.targetRole().isEmpty()) {
            checkArgument(renamed.isEmpty(),
                "%s carries both targetRole = \"%s\" and @UMLInclude(name = \"%s\"): two names "
                    + "for the same line. Keep only one of the two.",
                location(field), association.targetRole(), renamed);
            return association.targetRole();
        }
        return renamed.isEmpty() ? field.getName() : renamed;
    }

    public static String nameDisplayed(Method method) {
        String renamed = renamed(method,
            method.getDeclaringClass().getName() + "." + method.getName());
        return renamed.isEmpty() ? method.getName() : renamed;
    }

    private static String renamed(AnnotatedElement member, String location) {
        UMLInclude inclusion = member.getAnnotation(UMLInclude.class);
        if (inclusion == null || inclusion.name().isEmpty()) {
            return "";
        }
        checkArgument(!inclusion.name().isBlank(),
            "%s: @UMLInclude(name = ...) cannot be made of whitespace only.", location);
        return inclusion.name();
    }

    private static UmlField fieldAssociated(Field field, String name, UMLAssociation association,
        Rules rules) {
        String context = "@UMLAssociation on " + location(field);

        Class<?> target = InferredTarget.of(field, association, context);
        TargetResolver.validateTarget(target, context, "target()");

        try {
            UmlAssociation link =
                UmlAssociation.builder(field.getName(), target.getName())
                    .sourceMultiplicity(association.sourceMultiplicity())
                    .sourceRole(association.sourceRole())
                    .targetRole(association.targetRole())
                    .targetMultiplicity(association.targetMultiplicity())
                    .label(association.label())
                    .constraints(ConstraintExtractor.of(field, location(field)))
                    .build();

            ImmutableList<String> constraints = link.constraints();

            return attribute(field, name,

                target.getSimpleName(),
                link.targetMultiplicity(), constraints, rules);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(context + " : " + e.getMessage(), e);
        }
    }

    private static String returnType(Method method, String multiplicity, Rules rules) {
        if (rules.nameDisplayedReturn(method.getReturnType()).isEmpty()) {
            return "";
        }
        return propertyType(method.getGenericReturnType(), multiplicity, rules);
    }

    private static String symbol(int modifiers, Rules rules) {
        return rules.omitVisibility() ? "" : VisibilityPolicy.symbol(modifiers);
    }

    private static String location(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }

    private static String location(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    private static ImmutableList<UmlParameter> parameters(Method method, Rules rules) {
        ImmutableList.Builder<UmlParameter> parameters = ImmutableList.builder();

        for (Parameter parameter : method.getParameters()) {
            String type = parameterType(parameter, rules);

            parameters.add(parameter.isNamePresent() && !rules.parametersOmitNames()
                ? new UmlParameter(parameter.getName(), type)
                : UmlParameter.unnamed(type));
        }
        return parameters.build();
    }

    private static String parameterType(Parameter parameter, Rules rules) {
        String type = DisplayedType.of(parameter.getParameterizedType(), rules);
        return parameter.isVarArgs() && type.endsWith("[]")
            ? type.substring(0, type.length() - "[]".length()) + "..."
            : type;
    }

    public static ImmutableList<UmlMethod> extractMethods(Class<?> clazz, UMLVisibility strategy) {
        return extractMethods(clazz, strategy, Rules.no());
    }

    public static ImmutableList<UmlMethod> extractMethods(Class<?> clazz, UMLVisibility strategy,
        Rules rules) {
        ImmutableList.Builder<UmlMethod> result = ImmutableList.builder();

        for (Method method : OrderedMembers.methods(clazz, rules)) {
            if (method.isSynthetic()) {
                continue;
            }

            if (isAccessorPropagated(clazz, method)) {
                continue;
            }

            UMLInclude inclusion = method.getAnnotation(UMLInclude.class);

            if (inclusion != null && inclusion.asAttribute()) {
                continue;
            }

            Optional<String> name = kept(clazz, method, strategy, rules);
            if (name.isEmpty()) {
                continue;
            }

            try {
                String multiplicity = multiplicity(method, method.getGenericReturnType(), rules);
                result.add(new UmlMethod(
                    name.get(),
                    returnType(method, multiplicity, rules),
                    multiplicity,
                    symbol(method.getModifiers(), rules),
                    Modifier.isStatic(method.getModifiers()),
                    parameters(method, rules),
                    method.isAnnotationPresent(UMLDerived.class),

                    Modifier.isAbstract(method.getModifiers()) && !rules.abstractOmit()
                ));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException(
                    "@UMLMultiplicity on " + location(method) + " : " + e.getMessage(), e);
            }
        }
        return result.build();
    }

    public static ImmutableList<UmlField> methodsAsAttributes(Class<?> clazz,
        UMLVisibility strategy, Rules rules) {
        ImmutableList.Builder<UmlField> result = ImmutableList.builder();

        for (Method method : OrderedMembers.methods(clazz, rules)) {
            UMLInclude inclusion = method.getAnnotation(UMLInclude.class);
            if (method.isSynthetic() || inclusion == null || !inclusion.asAttribute()) {
                continue;
            }

            if (isAccessorPropagated(clazz, method)) {
                continue;
            }
            Optional<String> name = kept(clazz, method, strategy, rules);
            if (name.isEmpty()) {
                continue;
            }
            String location = location(method);
            checkArgument(method.getParameterCount() == 0,
                messageNotationAttributAvecParametres(location));
            checkArgument(method.getReturnType() != void.class,
                messageNotationAttributSansRetour(location));

            try {
                String multiplicity =
                    multiplicity(method, method.getGenericReturnType(), rules);
                result.add(attribute(method, name.get(),

                    propertyType(method.getGenericReturnType(), multiplicity, rules),
                    multiplicity, ConstraintExtractor.of(method, location), rules));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException(
                    "@UMLMultiplicity on " + location + " : " + e.getMessage(), e);
            }
        }
        return result.build();
    }

    private static boolean excludedByInheritance(Class<?> clazz, Method method) {
        if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers())) {
            return false;
        }
        for (Class<?> supertype : supertypes(clazz)) {
            for (Method declared : supertype.getDeclaredMethods()) {
                if (overrides(method, declared)
                    && declared.isAnnotationPresent(UMLExclude.class)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean overrides(Method candidate, Method declared) {
        if (!declared.getName().equals(candidate.getName())
            || declared.getParameterCount() != candidate.getParameterCount()) {
            return false;
        }
        Class<?>[] expected = declared.getParameterTypes();
        Class<?>[] written = candidate.getParameterTypes();
        for (int i = 0; i < expected.length; i++) {
            if (!expected[i].isAssignableFrom(written[i])) {
                return false;
            }
        }
        return true;
    }

    private static Set<Class<?>> supertypes(Class<?> clazz) {
        Set<Class<?>> seen = new LinkedHashSet<>();
        List<Class<?>> toVisit = new ArrayList<>();
        toVisit.add(clazz);

        while (!toVisit.isEmpty()) {
            Class<?> current = toVisit.remove(toVisit.size() - 1);
            Class<?> parent = current.getSuperclass();
            if (parent != null && seen.add(parent)) {
                toVisit.add(parent);
            }
            for (Class<?> interface_ : current.getInterfaces()) {
                if (seen.add(interface_)) {
                    toVisit.add(interface_);
                }
            }
        }
        return seen;
    }
}
