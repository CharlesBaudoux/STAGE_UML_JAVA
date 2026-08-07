package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.regex.Pattern;

// Un motif de nom, tel qu'on l'écrit dans le fichier de règles : « get* », « *Cache », « log ».
//
// L'étoile remplace n'importe quelle suite de caractères, y compris aucune ; tout le reste est
// pris au pied de la lettre. C'est la convention des noms de fichiers, celle que tout le monde
// connaît — et non les expressions régulières, dont la puissance se paierait ici en fautes de
// frappe silencieuses : un « . » écrit par mégarde y accepterait n'importe quel caractère au lieu
// de n'accepter qu'un point.
//
// La comparaison respecte la casse : « get* » ne masque pas « Get* ». Les noms Java ont une casse
// signifiante, et deviner l'intention serait une source d'écarts silencieux de plus.
public final class Motif {

    // Le motif tel qu'écrit, conservé pour les messages.
    private final String ecrit;

    // Sa traduction en expression régulière, calculée une fois : le motif sert une fois par
    // membre de chaque classe du projet.
    private final Pattern regex;

    private Motif(String ecrit, Pattern regex) {
        this.ecrit = ecrit;
        this.regex = regex;
    }

    public static Motif de(String ecrit) {
        checkNotNull(ecrit);
        checkArgument(!ecrit.isBlank(), "A pattern cannot be empty or made of whitespace only.");

        return new Motif(ecrit, Pattern.compile(enRegex(ecrit)));
    }

    // Traduit « get* » en « \Qget\E.* ».
    //
    // Pattern.quote encadre chaque morceau littéral de \Q…\E, ce qui neutralise d'un coup tous
    // les caractères que l'expression régulière interpréterait — le point, le dollar, les
    // parenthèses. Seules les étoiles écrites par l'utilisateur deviennent des « .* ».
    private static String enRegex(String motif) {
        StringBuilder regex = new StringBuilder();
        int debut = 0;

        while (true) {
            int etoile = motif.indexOf('*', debut);
            if (etoile < 0) {
                break;
            }
            // Le morceau qui précède l'étoile, s'il n'est pas vide : Pattern.quote("") produit
            // « \Q\E », inutile mais inoffensif ; on l'évite tout de même pour que la regex reste
            // lisible dans un débogueur.
            if (etoile > debut) {
                regex.append(Pattern.quote(motif.substring(debut, etoile)));
            }
            regex.append(".*");
            debut = etoile + 1;
        }
        if (debut < motif.length()) {
            regex.append(Pattern.quote(motif.substring(debut)));
        }
        return regex.toString();
    }

    public boolean correspond(String nom) {
        checkNotNull(nom);
        return regex.matcher(nom).matches();
    }

    public String ecrit() {
        return ecrit;
    }

    @Override
    public String toString() {
        return ecrit;
    }
}
