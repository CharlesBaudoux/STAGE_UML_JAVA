package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Optional;
import java.util.StringJoiner;

// Écrit un type tel qu'il paraîtra dans une boîte, paramètres de type compris.
//
// CE QUE L'EFFACEMENT DE TYPE EFFACE, ET CE QU'IL N'EFFACE PAS
//
// On lit souvent que la compilation perd les paramètres de type, et qu'un « List<Commande> » y
// devient un « List ». C'est vrai de ce qui vit à l'exécution : deux listes, l'une de commandes
// et l'autre de clients, sont le même objet en mémoire, et aucune ne sait ce qu'elle contient.
//
// Ce n'est pas vrai des signatures déclarées. Le compilateur écrit dans le fichier .class un
// attribut « Signature » qui conserve le type tel qu'il a été écrit, précisément pour que
// d'autres compilations puissent le relire. La réflexion l'expose : getGenericType() sur un
// attribut, getGenericReturnType() sur une méthode, getParameterizedType() sur un paramètre —
// là où getType() et getReturnType() ne rendent que le type effacé.
//
// La différence est visible sur un diagramme : « Map<String, Short> » dit ce que la table
// associe, « Map » ne dit rien. C'est aussi ce qui permet de déduire la classe visée par une
// association sans la déclarer (voir CibleDeduite).
public final class TypeAffiche {

    // Les conteneurs dont on sait extraire l'élément quand une multiplicité est écrite.
    //
    // Optional y figure au même titre que les collections : « Optional<Integer> [0..1] » dit
    // « zéro ou un entier », et c'est l'entier qui est la propriété, pas l'enveloppe.
    private static final Class<?>[] CONTENEURS =
        {Iterable.class, Optional.class};

    private TypeAffiche() {}

    // Le type, écrit avec ses paramètres : « Map<String, Short> », « List<Commande> ».
    //
    // Chaque nom de classe rencontré passe par les règles du projet, y compris à l'intérieur des
    // chevrons : un « ImmutableMap<String, ImmutableSet<Commande>> » s'écrit donc
    // « Map<String, Set<Commande>> » si le projet le demande.
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
            // « ? extends Commande » garde sa borne, qui dit quelque chose ; « ? super X » et le
            // joker nu se réduisent à « ? », la borne inférieure n'apprenant rien au lecteur.
            Type[] hautes = joker.getUpperBounds();
            if (hautes.length == 1 && hautes[0] != Object.class) {
                return "? extends " + de(hautes[0], regles);
            }
            return "?";
        }
        if (type instanceof TypeVariable<?> variable) {
            // Le T d'une classe générique : son nom est tout ce qu'on peut en dire, et c'est ce
            // que le lecteur du diagramme attend d'y lire.
            return variable.getName();
        }
        // Aucune autre sorte de Type n'existe dans les signatures Java ; le repli protège d'une
        // évolution du langage plutôt que d'un cas connu.
        return type.getTypeName();
    }

    // Le type à écrire devant une multiplicité.
    //
    // UML veut alors le type de l'ÉLÉMENT, jamais celui du conteneur. « aliases: Set [0..*] » se
    // lit « zéro ou plusieurs ensembles », ce qui est faux : il y a un ensemble, contenant zéro
    // ou plusieurs chaînes. La forme juste est « aliases: String [0..*] ».
    //
    // L'enveloppe n'est retirée que si elle en est une — un Iterable ou un Optional — et qu'elle
    // n'a qu'un seul paramètre de type. Une Map en a deux, et rien ne dirait lequel est la
    // propriété : elle est alors écrite en entier, ce qui reste exact.
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
        return de(arguments[0], regles);
    }

    // L'élément d'une collection, quand le type en désigne un seul : « List<Commande> » donne
    // Commande. Null si le type n'est pas une collection paramétrée, ou en désigne plusieurs.
    //
    // Sert à déduire la classe visée par une association que l'utilisateur n'a pas nommée.
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
