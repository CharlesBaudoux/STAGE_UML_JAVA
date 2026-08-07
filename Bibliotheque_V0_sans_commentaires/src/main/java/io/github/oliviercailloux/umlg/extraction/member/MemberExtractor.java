package io.github.oliviercailloux.umlg.extraction.member;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.ConstraintExtractor;
import io.github.oliviercailloux.umlg.extraction.MembresOrdonnes;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

// Remplit les deux compartiments d'une boîte : les attributs et les méthodes.
//
// Tout l'enjeu est de trier. Une classe réelle a beaucoup plus de membres qu'un diagramme
// lisible n'en supporte ; trois filtres se succèdent donc, dans cet ordre :
//   1. le technique — ce que le compilateur a fabriqué (isSynthetic) ;
//   2. le sémantique — ce qui est déjà montré ailleurs (associations), ou n'apprend rien
//      (NoiseFilter : getters, setters, equals…) ;
//   3. le choix de l'utilisateur — la stratégie de visibilité, et les vetos @UMLInclude /
//      @UMLExclude (VisibilityPolicy).
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
        ImmutableList.Builder<UmlField> result = ImmutableList.builder();

        for (Field field : MembresOrdonnes.champs(clazz)) {

            if (field.isSynthetic()) {
                continue;
            }

            if (field.isEnumConstant()) {
                continue;
            }

            if (field.isAnnotationPresent(UMLAssociation.class)) {
                continue;
            }

            boolean visible = VisibilityPolicy.estVisible(field.getModifiers(), strategy,
                field.isAnnotationPresent(UMLInclude.class),
                field.isAnnotationPresent(UMLExclude.class));

            if (visible) {
                result.add(new UmlField(
                    field.getName(),

                    field.getType().getSimpleName(),
                    VisibilityPolicy.symbole(field.getModifiers()),
                    Modifier.isStatic(field.getModifiers()),
                    field.isAnnotationPresent(UMLDerived.class),
                    ConstraintExtractor.de(field, emplacement(field))
                ));
            }
        }
        return result.build();
    }

    private static String emplacement(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }

    public static ImmutableList<UmlMethod> extractMethods(Class<?> clazz, UMLVisibility strategy) {
        ImmutableList.Builder<UmlMethod> result = ImmutableList.builder();

        for (Method method : MembresOrdonnes.methodes(clazz)) {

            if (method.isSynthetic()) {
                continue;
            }

            boolean hasInclude = method.isAnnotationPresent(UMLInclude.class);
            boolean hasExclude = method.isAnnotationPresent(UMLExclude.class);

            if (NoiseFilter.estDuBruit(method, clazz) && !hasInclude) {
                continue;
            }

            if (VisibilityPolicy.estVisible(method.getModifiers(), strategy, hasInclude,
                hasExclude)) {

                ImmutableList<String> paramTypes = Arrays.stream(method.getParameterTypes())
                    .map(Class::getSimpleName)
                    .collect(ImmutableList.toImmutableList());

                result.add(new UmlMethod(
                    method.getName(),
                    method.getReturnType().getSimpleName(),
                    VisibilityPolicy.symbole(method.getModifiers()),
                    Modifier.isStatic(method.getModifiers()),
                    paramTypes,
                    method.isAnnotationPresent(UMLDerived.class)
                ));
            }
        }
        return result.build();
    }
}
