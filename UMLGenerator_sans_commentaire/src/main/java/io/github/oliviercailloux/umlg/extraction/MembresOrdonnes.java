package io.github.oliviercailloux.umlg.extraction;

import io.github.oliviercailloux.umlg.Avertissements;
import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

public final class MembresOrdonnes {

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

    private static int rang(ImmutableList<String> ordre, String cle) {
        int rang = ordre.indexOf(cle);
        return rang < 0 ? Integer.MAX_VALUE : rang;
    }

    private static void signalerOrdrePerdu(Class<?> clazz) {
        Avertissements.dire("\"order declaration\" is asked for, but the class file of "
            + clazz.getName() + " cannot be re-read; the members of this box fall back to "
            + "alphabetical order.");
    }

    private static String parametres(Method method) {
        return Arrays.stream(method.getParameterTypes())
            .map(Class::getName)
            .collect(Collectors.joining(","));
    }
}
