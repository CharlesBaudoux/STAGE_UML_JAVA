package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Moteur de lecture par réflexion : traduit une {@code Class<?>} annotée
 * {@code @UMLClass} en {@link UmlEntity}, en appliquant la stratégie de
 * visibilité et le filtre anti-bruit décrits dans les spécifications.
 */
public final class EntityExtractor {

    // Lettres/chiffres unicode, espace, '.', '_' et '-' : suffisant pour des noms de
    // classe réalistes (y compris accentués), tout en excluant les caractères qui
    // casseraient ou détourneraient la syntaxe PlantUML (guillemets, chevrons, retours
    // à la ligne, etc.) puisque name() est inséré tel quel entre guillemets dans le rendu.
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N} _.\\-]+$");

    // Même logique que pour le nom, mais stereotype() est inséré entre << >> : les
    // chevrons et guillemets doivent rester exclus.
    private static final Pattern SAFE_STEREOTYPE_PATTERN = Pattern.compile("^[\\p{L}\\p{N} _\\-]+$");

    // 3 ou 6 chiffres hexadécimaux, avec ou sans '#' en tête (ex: "AABBCC", "#FF0000", "#ABC").
    private static final Pattern COLOR_PATTERN =
        Pattern.compile("^#?([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$");

    private EntityExtractor() {}

    public static UmlEntity extract(Class<?> clazz) {
        checkNotNull(clazz);
        checkArgument(clazz.isAnnotationPresent(UMLClass.class),
            "La classe %s ne possède pas l'annotation @UMLClass.", clazz.getName());

        UMLClass annotation = clazz.getAnnotation(UMLClass.class);

        String finalName = annotation.name().isEmpty() ? clazz.getSimpleName() : annotation.name();
        String finalStereotype = annotation.stereotype();
        String finalColor = annotation.color();
        UMLVisibility finalStrategy = annotation.strategy();

        validateName(finalName, clazz);
        validateStereotype(finalStereotype, clazz);
        validateColor(finalColor, clazz);

        List<UmlField> extractedFields = extractFields(clazz, finalStrategy);
        List<UmlMethod> extractedMethods = extractMethods(clazz, finalStrategy);

        return new UmlEntity(
            finalName,
            finalStereotype,
            finalColor,
            finalStrategy,
            extractedFields,
            extractedMethods
        );
    }

    static void validateName(String name, Class<?> clazz) {
        checkArgument(!name.isBlank(),
            "@UMLClass sur %s : name() ne peut pas être vide ou composé uniquement d'espaces.",
            clazz.getName());
        checkArgument(SAFE_NAME_PATTERN.matcher(name).matches(),
            "@UMLClass sur %s : name() = \"%s\" contient des caractères non autorisés "
                + "(seuls lettres, chiffres, espaces, '.', '_' et '-' sont acceptés).",
            clazz.getName(), name);
    }

    static void validateStereotype(String stereotype, Class<?> clazz) {
        if (stereotype.isEmpty()) {
            return;
        }
        checkArgument(!stereotype.isBlank(),
            "@UMLClass sur %s : stereotype() ne peut pas être composé uniquement d'espaces.",
            clazz.getName());
        checkArgument(SAFE_STEREOTYPE_PATTERN.matcher(stereotype).matches(),
            "@UMLClass sur %s : stereotype() = \"%s\" contient des caractères non autorisés "
                + "(seuls lettres, chiffres, espaces, '_' et '-' sont acceptés).",
            clazz.getName(), stereotype);
    }

    static void validateColor(String color, Class<?> clazz) {
        if (color.isEmpty()) {
            return;
        }
        checkArgument(COLOR_PATTERN.matcher(color).matches(),
            "@UMLClass sur %s : color() = \"%s\" n'est pas un code couleur hexadécimal valide "
                + "(attendu : 3 ou 6 chiffres hexadécimaux, avec ou sans '#' en tête, ex. \"#FF0000\").",
            clazz.getName(), color);
    }

    private static List<UmlField> extractFields(Class<?> clazz, UMLVisibility strategy) {
        List<UmlField> result = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (isElementVisible(field.getModifiers(), strategy, field.isAnnotationPresent(UMLInclude.class), field.isAnnotationPresent(UMLExclude.class))) {
                result.add(new UmlField(
                    field.getName(),
                    field.getType().getSimpleName(),
                    getUmlVisibilitySymbol(field.getModifiers()),
                    Modifier.isStatic(field.getModifiers())
                ));
            }
        }
        return result;
    }


    private static List<UmlMethod> extractMethods(Class<?> clazz, UMLVisibility strategy) {
        List<UmlMethod> result = new ArrayList<>();

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }

            boolean hasInclude = method.isAnnotationPresent(UMLInclude.class);
            boolean hasExclude = method.isAnnotationPresent(UMLExclude.class);

            if (isNoise(method.getName()) && !hasInclude) {
                continue;
            }

            if (isElementVisible(method.getModifiers(), strategy, hasInclude, hasExclude)) {

                List<String> paramTypes = Arrays.stream(method.getParameterTypes())
                                                .map(Class::getSimpleName)
                                                .toList();

                result.add(new UmlMethod(
                    method.getName(),
                    method.getReturnType().getSimpleName(),
                    getUmlVisibilitySymbol(method.getModifiers()),
                    Modifier.isStatic(method.getModifiers()),
                    paramTypes
                ));
            }
        }
        return result;
    }


    private static boolean isElementVisible(int modifiers, UMLVisibility strategy, boolean hasInclude, boolean hasExclude) {
        
        if (hasExclude) return false;

        if (hasInclude) return true;

        if (strategy == UMLVisibility.NONE) return false;
        if (strategy == UMLVisibility.PRIVATE) return true; 

        if (strategy == UMLVisibility.PUBLIC) {
            return Modifier.isPublic(modifiers);
        }

        if (strategy == UMLVisibility.PROTECTED) {
            return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
        }

        if (strategy == UMLVisibility.PACKAGE) {
            
            boolean isPackagePrivate = !Modifier.isPublic(modifiers)
                                    && !Modifier.isProtected(modifiers)
                                    && !Modifier.isPrivate(modifiers);
            return Modifier.isPublic(modifiers) || isPackagePrivate;
        }

        return false;
    }

    private static boolean isNoise(String methodName) {
        return methodName.startsWith("get")
            || methodName.startsWith("set")
            || methodName.startsWith("is")
            || methodName.equals("toString")
            || methodName.equals("hashCode")
            || methodName.equals("equals");
    }

    private static String getUmlVisibilitySymbol(int modifiers) {
        if (Modifier.isPublic(modifiers)) return "+";
        if (Modifier.isPrivate(modifiers)) return "-";
        if (Modifier.isProtected(modifiers)) return "#";
        return "~"; 
    }
}
