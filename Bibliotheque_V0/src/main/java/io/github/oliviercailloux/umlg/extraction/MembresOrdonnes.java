package io.github.oliviercailloux.umlg.extraction;

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

// Rend les membres d'une classe dans un ordre qui ne dépend que de la classe.
//
// POURQUOI CE TRI EXISTE
//
// getDeclaredFields et getDeclaredMethods rendent les membres « dans un ordre non spécifié »,
// dit la documentation de Java — et ce n'est pas une précaution de style. Deux exécutions de la
// même compilation, sur la même machine et le même fichier .class, ont produit ici deux ordres
// différents. La cause tient à l'état interne de la machine virtuelle, que le reste du programme
// influence.
//
// Sans ce tri, la bibliothèque n'est donc pas reproductible : deux générations du même projet
// peuvent écrire les attributs et les méthodes dans un ordre différent, et donc produire deux
// fichiers différents. C'est gênant pour qui voudrait versionner le diagramme engendré, et
// rédhibitoire pour le test de référence, qui compare un document entier.
//
// L'ORDRE RETENU
//
// L'ordre alphabétique, faute de mieux : l'ordre de déclaration du source serait plus naturel à
// lire, mais il ne survit pas à la compilation — la réflexion ne le connaît pas. Entre un ordre
// arbitraire et stable et un ordre naturel mais imprévisible, la stabilité l'emporte : elle
// seule permet de comparer deux diagrammes.
//
// Les littéraux d'une énumération font exception et ne passent pas par ici : leur ordre de
// déclaration, lui, est porté par la notion d'ordinal, et il a un sens que l'alphabet
// détruirait. MemberExtractor les lit avec getEnumConstants, qui les rend dans cet ordre.
public final class MembresOrdonnes {

    // Le nom d'abord ; à noms égaux — des surcharges — les types des paramètres départagent.
    private static final Comparator<Method> PAR_SIGNATURE =
        Comparator.comparing(Method::getName).thenComparing(MembresOrdonnes::parametres);

    private MembresOrdonnes() {}

    public static ImmutableList<Field> champs(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
            .sorted(Comparator.comparing(Field::getName))
            .collect(ImmutableList.toImmutableList());
    }

    public static ImmutableList<Method> methodes(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
            .sorted(PAR_SIGNATURE)
            .collect(ImmutableList.toImmutableList());
    }

    // Les noms complets des types de paramètres, dans l'ordre de la signature. Le nom complet et
    // non le nom simple : deux classes homonymes de paquetages différents doivent départager.
    private static String parametres(Method method) {
        return Arrays.stream(method.getParameterTypes())
            .map(Class::getName)
            .collect(Collectors.joining(","));
    }
}
