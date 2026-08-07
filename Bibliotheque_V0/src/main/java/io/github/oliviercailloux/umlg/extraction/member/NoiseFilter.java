package io.github.oliviercailloux.umlg.extraction.member;

import io.github.oliviercailloux.umlg.extraction.rules.Bruit;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

// Reconnaît les membres que le projet a déclaré sans intérêt pour son diagramme.
//
// RIEN N'EST MASQUÉ SANS DEMANDE
//
// La bibliothèque écartait autrefois d'office les accesseurs, les méthodes d'Object et quelques
// autres familles, au motif qu'elles n'apprennent rien. Le motif est bon dans beaucoup de
// projets, et mauvais dans d'autres : sur un code immuable à la manière de Guava, il n'y a pas un
// getter à écarter, et ce qui encombre la boîte est ailleurs — des surcharges, des méthodes de
// fabrique. Un filtre qui décide seul retire alors du diagramme des membres que personne n'avait
// demandé à cacher, sans que rien ne le signale.
//
// Chaque famille se déclare donc dans le fichier de règles, et rien ne s'applique tant qu'elle
// n'y est pas écrite. Ce qui reste ici est la reconnaissance : savoir dire ce qu'EST un getter,
// et non décider s'il faut le montrer.
//
// Chaque test vérifie la forme complète (nom, nombre de paramètres, type de retour) et pas
// seulement le nom : une méthode métier appelée getPrix(Client) n'est pas un getter, et ne doit
// donc pas disparaître avec eux.
final class NoiseFilter {

    private NoiseFilter() {}

    // Cette méthode est-elle du bruit aux yeux de ce projet ?
    //
    // Chaque famille n'est consultée que si le fichier de règles la nomme ; les motifs de noms
    // s'y ajoutent, pour ce qu'aucune famille ne sait reconnaître — les « with… » d'un builder,
    // les « visit… » d'un visiteur.
    static boolean estDuBruit(Method method, Class<?> clazz, Regles regles) {
        return (regles.masque(Bruit.OBJECT_METHODS) && isObjectMethod(method))
            || (regles.masque(Bruit.ACCESSORS) && isAccessor(method))
            || (regles.masque(Bruit.RECORD_ACCESSORS) && isRecordAccessor(method, clazz))
            || (regles.masque(Bruit.ENUM_METHODS) && isEnumGenerated(method, clazz))
            || regles.methodeMasquee(method.getName());
    }

    // Cet attribut est-il du bruit ?
    //
    // Les attributs n'ont pas de famille : aucune forme ne les trahit comme la convention
    // JavaBean trahit un getter. Un logger ne se reconnaît qu'à son nom ou à son type, tous deux
    // affaires de convention locale — d'où les seuls motifs, écrits par qui connaît le projet.
    static boolean estDuBruit(Field field, Regles regles) {
        return regles.champMasque(field.getName());
    }

    // Les trois formes de la convention JavaBean, réunies : c'est ainsi que le fichier les
    // désigne, d'un seul mot.
    private static boolean isAccessor(Method method) {
        return isGetter(method) || isBooleanGetter(method) || isSetter(method);
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
