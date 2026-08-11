package io.github.oliviercailloux.umlg.extraction;

import io.github.oliviercailloux.umlg.Avertissements;
import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
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
// LES DEUX ORDRES OFFERTS
//
// L'ordre alphabétique est le défaut : arbitraire mais stable, il ne dépend que des noms. Le
// projet peut préférer l'ordre de déclaration (« order declaration » dans umlg.rules) : la
// réflexion l'ignore, mais le fichier .class le connaît — ses tables de membres sont écrites
// dans l'ordre du source par les compilateurs en usage, et le fichier ne change pas entre deux
// lectures, ce qui préserve la reproductibilité (voir OrdreDuFichierClasse). Si le fichier ne
// peut pas être relu, l'alphabet reprend la main, et un avertissement le dit : un ordre demandé
// et non rendu ne doit pas passer inaperçu.
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
        return champs(clazz, Regles.aucune());
    }

    public static ImmutableList<Field> champs(Class<?> clazz, Regles regles) {
        if (regles.ordreDeclaration()) {
            ImmutableList<String> ordre = OrdreDuFichierClasse.champs(clazz);
            if (ordre != null) {
                return Arrays.stream(clazz.getDeclaredFields())
                    .sorted(Comparator.comparingInt(champ -> rang(ordre, champ.getName())))
                    .collect(ImmutableList.toImmutableList());
            }
            signalerOrdrePerdu(clazz);
        }
        return Arrays.stream(clazz.getDeclaredFields())
            .sorted(Comparator.comparing(Field::getName))
            .collect(ImmutableList.toImmutableList());
    }

    public static ImmutableList<Method> methodes(Class<?> clazz) {
        return methodes(clazz, Regles.aucune());
    }

    public static ImmutableList<Method> methodes(Class<?> clazz, Regles regles) {
        if (regles.ordreDeclaration()) {
            ImmutableList<String> ordre = OrdreDuFichierClasse.methodes(clazz);
            if (ordre != null) {
                return Arrays.stream(clazz.getDeclaredMethods())
                    .sorted(Comparator.comparingInt(
                        methode -> rang(ordre, OrdreDuFichierClasse.cle(methode))))
                    .collect(ImmutableList.toImmutableList());
            }
            signalerOrdrePerdu(clazz);
        }
        return Arrays.stream(clazz.getDeclaredMethods())
            .sorted(PAR_SIGNATURE)
            .collect(ImmutableList.toImmutableList());
    }

    // La place d'un membre dans l'ordre du fichier. Un membre que la réflexion connaît figure
    // toujours dans le fichier ; si la correspondance échouait malgré tout, il partirait en fin
    // de boîte plutôt qu'en tête — une place discrète pour un cas qui ne devrait pas exister.
    private static int rang(ImmutableList<String> ordre, String cle) {
        int rang = ordre.indexOf(cle);
        return rang < 0 ? Integer.MAX_VALUE : rang;
    }

    // L'ordre de déclaration a été demandé et ne peut pas être rendu pour cette classe : le dire
    // vaut mieux que de laisser l'utilisateur chercher pourquoi une boîte est restée alphabétique.
    private static void signalerOrdrePerdu(Class<?> clazz) {
        Avertissements.dire("\"order declaration\" is asked for, but the class file of "
            + clazz.getName() + " cannot be re-read; the members of this box fall back to "
            + "alphabetical order.");
    }

    // Les noms complets des types de paramètres, dans l'ordre de la signature. Le nom complet et
    // non le nom simple : deux classes homonymes de paquetages différents doivent départager.
    private static String parametres(Method method) {
        return Arrays.stream(method.getParameterTypes())
            .map(Class::getName)
            .collect(Collectors.joining(","));
    }
}
