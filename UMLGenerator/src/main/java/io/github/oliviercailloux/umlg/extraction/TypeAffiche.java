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

    // Le nom primitif de chaque type enveloppe, pour le déballage d'un élément de conteneur.
    //
    // Java interdit « Optional<int> » : l'enveloppe est une contrainte du langage, pas un choix
    // de l'auteur. Quand le conteneur est retiré, la garder écrirait « Integer [0..1] » là où la
    // propriété est un entier — le diagramme doit dire « int », comme l'aurait écrit l'auteur si
    // le langage l'avait laissé faire.
    private static final Map<Class<?>, String> PRIMITIFS = Map.of(
        Boolean.class, "boolean", Byte.class, "byte", Character.class, "char",
        Short.class, "short", Integer.class, "int", Long.class, "long",
        Float.class, "float", Double.class, "double");

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
    // L'enveloppe n'est retirée que si elle en est une — un Iterable, un Optional, ou un tableau,
    // qui est la façon la plus ancienne qu'a Java d'écrire « plusieurs ». Un conteneur à deux
    // paramètres de type n'en est pas une : une Map est alors écrite en entier, rien ne disant
    // lequel des deux serait la propriété, et cela reste exact.
    public static String deLElement(Type type, Regles regles) {
        checkNotNull(type);
        checkNotNull(regles);

        ParameterizedType conteneur = conteneurAUnElement(type);
        if (conteneur != null) {
            Type element = conteneur.getActualTypeArguments()[0];
            // Un élément enveloppé — Integer dans Optional<Integer> — redevient primitif, sauf si
            // une règle du projet a donné à l'enveloppe un autre nom : la demande explicite
            // l'emporte. Le déballage ne vaut que pour un conteneur : « Integer[] » est un choix
            // de l'auteur, que « int[] » lui offrait déjà.
            if (element instanceof Class<?> classe) {
                String primitif = PRIMITIFS.get(classe);
                if (primitif != null && regles.nomAffiche(classe).equals(classe.getSimpleName())) {
                    return primitif;
                }
            }
            return de(sansJoker(element), regles);
        }
        // Un tableau devant une multiplicité perd ses crochets : « String[] [1..*] » se lirait
        // « un ou plusieurs tableaux de chaînes », alors que le code n'en déclare qu'un, contenant
        // une ou plusieurs chaînes. Le tableau EST la multiplicité, exactement comme le conteneur.
        Type composante = composanteDeTableau(type);
        if (composante != null) {
            return de(composante, regles);
        }
        return de(type, regles);
    }

    // Le type vu comme un conteneur ne portant qu'un seul élément — « Set<X> », « Optional<X> » —,
    // ou null.
    //
    // Les trois questions que cette classe pose devant une multiplicité — quel type écrire,
    // quelle multiplicité déduire, quelle classe l'association vise — ont la même condition
    // d'entrée, et elle s'écrit donc ici seulement : un type paramétré, dont le type brut est un
    // conteneur, et qui n'a qu'un seul argument de type. Une Map en a deux, un type brut aucun —
    // l'élément serait indicible, et la multiplicité avec lui.
    private static ParameterizedType conteneurAUnElement(Type type) {
        if (!(type instanceof ParameterizedType parametre)) {
            return null;
        }
        if (parametre.getActualTypeArguments().length != 1
            || !estUnConteneur(parametre.getRawType())) {
            return null;
        }
        return parametre;
    }

    // Ce dont ce type est un tableau, ou null s'il n'en est pas un. Les deux formes que rend la
    // réflexion sont couvertes : « String[] » est une Class, « T[] » un GenericArrayType.
    private static Type composanteDeTableau(Type type) {
        if (type instanceof GenericArrayType tableau) {
            return tableau.getGenericComponentType();
        }
        if (type instanceof Class<?> classe && classe.isArray()) {
            return classe.getComponentType();
        }
        return null;
    }

    // Le joker retiré devant une multiplicité : « ? extends CharSequence [0..*] » se lit « zéro ou
    // plusieurs choses qui étendent CharSequence », là où « CharSequence [0..*] » dit la même
    // chose et se lit. La borne était tout ce que le joker apportait ; sans elle — un « ? » nu, un
    // « ? super X » —, il s'écrit tel quel, comme partout ailleurs.
    private static Type sansJoker(Type type) {
        if (type instanceof WildcardType joker) {
            Type[] hautes = joker.getUpperBounds();
            if (hautes.length == 1 && hautes[0] != Object.class) {
                return hautes[0];
            }
        }
        return type;
    }

    // La multiplicité que la forme du type déclare, ou la chaîne vide.
    //
    // « Optional<X> » dit « zéro ou un », un Iterable paramétré dit « zéro ou plusieurs » : ce
    // n'est pas deviné, c'est lu — le conteneur est la façon dont Java écrit ces multiplicités.
    // La règle reste opt-in (« "multiplicity": "brackets" » dans umlg.json) : la traduction change la
    // forme de toutes les lignes à collection, et c'est au projet de choisir sa notation. Elle ne
    // dit jamais plus que le type : « 0..* » et non « 1..* », qu'aucun Set ne garantit.
    //
    // La même condition d'entrée que deLElement, et écrite au même endroit : un conteneur d'un
    // seul paramètre de type (voir conteneurAUnElement).
    public static String multipliciteDeduite(Type type) {
        checkNotNull(type);

        ParameterizedType conteneur = conteneurAUnElement(type);
        if (conteneur == null) {
            return "";
        }
        Class<?> brut = (Class<?>) conteneur.getRawType();
        return Optional.class.isAssignableFrom(brut) ? "0..1" : "0..*";
    }

    // L'élément d'une collection, quand le type en désigne un seul : « List<Commande> » donne
    // Commande. Null si le type n'est pas une collection paramétrée, ou en désigne plusieurs.
    //
    // Sert à déduire la classe visée par une association que l'utilisateur n'a pas nommée.
    public static Class<?> elementSeul(Type type) {
        checkNotNull(type);

        ParameterizedType conteneur = conteneurAUnElement(type);
        if (conteneur == null) {
            return null;
        }
        return conteneur.getActualTypeArguments()[0] instanceof Class<?> classe ? classe : null;
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
