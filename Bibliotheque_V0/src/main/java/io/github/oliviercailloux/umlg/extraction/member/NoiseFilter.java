package io.github.oliviercailloux.umlg.extraction.member;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

// Reconnaît les méthodes qui n'apprennent rien au lecteur du diagramme.
//
// Un getSolde() ne dit rien de plus que l'attribut solde ; un equals() est attendu partout. Les
// afficher noierait les quelques méthodes qui, elles, portent le métier. Ce filtre est un
// jugement de valeur assumé, et il se contourne membre par membre avec @UMLInclude.
//
// Chaque test vérifie la forme complète (nom, nombre de paramètres, type de retour) et pas
// seulement le nom : une méthode métier appelée getPrix(Client) n'est pas un getter, et doit
// donc rester visible.
final class NoiseFilter {

    private NoiseFilter() {}

    // Point d'entrée : le OU des six familles reconnues. L'évaluation en court-circuit s'arrête
    // au premier test satisfait.
    static boolean estDuBruit(Method method, Class<?> clazz) {
        return isObjectMethod(method)
            || isGetter(method)
            || isBooleanGetter(method)
            || isSetter(method)
            || isRecordAccessor(method, clazz)
            || isEnumGenerated(method, clazz);
    }

    // Convention JavaBean : le préfixe doit être suivi d'au moins un caractère, en majuscule.
    // « getter » n'est donc pas un getter, « get() » non plus, mais « getX() » oui.
    private static boolean hasBeanPrefix(String methodName, String prefix) {
        return methodName.length() > prefix.length()
            && methodName.startsWith(prefix)
            && Character.isUpperCase(methodName.charAt(prefix.length()));
    }

    // getX() : aucun paramètre, et quelque chose à rendre.
    private static boolean isGetter(Method method) {
        return hasBeanPrefix(method.getName(), "get")
            && method.getParameterCount() == 0
            && method.getReturnType() != void.class;
    }

    // isX() : la variante booléenne, primitive ou objet.
    private static boolean isBooleanGetter(Method method) {
        return hasBeanPrefix(method.getName(), "is")
            && method.getParameterCount() == 0
            && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class);
    }

    // setX(v) : exactement un paramètre, rien en retour. Un setter « fluide » qui rendrait this
    // n'entre pas dans ce cas et reste affiché.
    private static boolean isSetter(Method method) {
        return hasBeanPrefix(method.getName(), "set")
            && method.getParameterCount() == 1
            && method.getReturnType() == void.class;
    }

    // Les trois méthodes héritées d'Object que tout le monde redéfinit. La signature exacte est
    // vérifiée : un equals(Client) surchargé n'est pas celui d'Object, et mérite d'être montré.
    private static boolean isObjectMethod(Method method) {
        String name = method.getName();
        Class<?>[] params = method.getParameterTypes();

        return switch (name) {
            case "toString" -> params.length == 0 && method.getReturnType() == String.class;
            case "hashCode" -> params.length == 0 && method.getReturnType() == int.class;
            case "equals" -> params.length == 1 && params[0] == Object.class
                && method.getReturnType() == boolean.class;
            default -> false;
        };
    }

    // Accesseur de record : la méthode nom() engendrée pour la composante nom. Elle ne suit pas
    // la convention get/is, d'où ce test propre aux records. On compare aussi le type de retour,
    // pour ne pas confondre avec une méthode homonyme écrite à la main.
    private static boolean isRecordAccessor(Method method, Class<?> clazz) {
        if (!clazz.isRecord() || method.getParameterCount() != 0) {
            return false;
        }
        return Arrays.stream(clazz.getRecordComponents())
                     .anyMatch(component -> component.getName().equals(method.getName())
                         && component.getType() == method.getReturnType());
    }

    // values() et valueOf(String) : le compilateur les ajoute à tout enum. Elles sont statiques,
    // ce qui écarte d'emblée les méthodes d'instance de même nom.
    private static boolean isEnumGenerated(Method method, Class<?> clazz) {
        if (!clazz.isEnum() || !Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        Class<?>[] params = method.getParameterTypes();

        return (method.getName().equals("values") && params.length == 0)
            || (method.getName().equals("valueOf") && params.length == 1
                && params[0] == String.class);
    }
}
