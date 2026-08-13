package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La grammaire d'une multiplicité UML, en un seul endroit.
 *
 * <p>Une multiplicité est soit {@code *}, soit un entier, soit un intervalle {@code min..max} dont
 * la borne haute peut être {@code *} et dont la borne basse ne dépasse pas la haute. La chaîne
 * vide est licite : c'est ainsi qu'un membre dit n'en porter aucune, ce qui vaut « exactement un »
 * en UML.
 *
 * <p><b>Pourquoi cette classe est publique</b>, alors que {@code Contraintes}, son voisin, ne
 * l'est pas. Deux moitiés de la bibliothèque doivent s'accorder sur cette grammaire : le modèle,
 * qui refuse une multiplicité impossible à la construction, et le processeur d'annotations, qui
 * doit refuser la même à la compilation — sans quoi {@code @UMLMultiplicity("3..1")} passerait
 * javac pour échouer à la génération. Le processeur vit dans un autre paquetage, et n'atteignait
 * la règle qu'en construisant un {@link UmlField} jetable dont il ignorait tout sauf qu'il
 * validerait la chaîne au passage. Ce détour marchait, et se serait mis à mentir le jour où le
 * constructeur d'{@code UmlField} aurait vérifié autre chose : l'erreur aurait parlé du nom ou du
 * type là où l'utilisateur avait écrit une multiplicité.
 *
 * <p>La grammaire est donc nommée, et la porte est franche. Elle est du même coup lisible par
 * l'utilisateur, comme {@code UMLGenerator.knownColors()} l'est pour les couleurs : ce que la
 * bibliothèque accepte est une question qu'on a le droit de lui poser.
 */
public final class Multiplicites {

    private Multiplicites() {}

    // Les groupes servent aussi à la comparaison des bornes : groupe 1 = borne basse, groupe 3 =
    // borne haute. La limite à 9 chiffres évite un débordement au parseLong.
    private static final Pattern CARDINALITE =
        Pattern.compile("\\*|(\\d{1,9})(\\.\\.(\\d{1,9}|\\*))?");

    /**
     * Vérifie qu'une chaîne est une multiplicité UML.
     *
     * @param multiplicite la chaîne à vérifier. Vide, elle est acceptée : c'est l'absence de
     *     multiplicité.
     * @param parametre le nom à donner à cette valeur dans le message d'erreur, pour que celui-ci
     *     désigne ce que l'appelant a écrit.
     * @throws IllegalArgumentException si la chaîne n'est pas une multiplicité, ou si sa borne
     *     basse dépasse sa borne haute.
     */
    public static void validate(String multiplicite, String parametre) {
        String probleme = probleme(multiplicite, parametre);
        checkArgument(probleme == null, probleme);
    }

    /**
     * Cette chaîne est-elle une multiplicité UML ?
     *
     * <p>La question que la classe promettait de savoir répondre, et qu'elle n'offrait pourtant
     * qu'en levant : un appelant qui voulait simplement savoir devait entourer son appel d'un
     * {@code try}, et se servir d'une exception comme d'une réponse. Ce que la bibliothèque
     * accepte est une question qu'on a le droit de lui poser sans en être puni.
     *
     * @param multiplicite la chaîne à examiner. Vide, elle est licite : c'est l'absence de
     *     multiplicité.
     * @return vrai si {@link #validate} l'accepterait.
     */
    public static boolean isValid(String multiplicite) {
        return probleme(multiplicite, "multiplicity") == null;
    }

    // Ce qui cloche dans cette chaîne, ou null si rien n'y cloche.
    //
    // Le tronc commun aux deux questions, pour que « est-ce valide ? » et « refuse si ce ne l'est
    // pas » ne puissent pas répondre différemment.
    private static String probleme(String multiplicite, String parametre) {
        if (multiplicite.isEmpty()) {
            return null;
        }

        Matcher m = CARDINALITE.matcher(multiplicite);
        if (!m.matches()) {
            return parametre + " = \"" + multiplicite + "\" is not a valid UML multiplicity "
                + "(expected: an integer, \"*\", or a range such as \"0..1\" or \"1..*\").";
        }

        String borneHaute = m.group(3);

        if (borneHaute == null || borneHaute.equals("*")) {
            return null;
        }
        long min = Long.parseLong(m.group(1));
        long max = Long.parseLong(borneHaute);

        if (min > max) {
            return parametre + " = \"" + multiplicite
                + "\": the lower bound exceeds the upper bound.";
        }
        return null;
    }
}
